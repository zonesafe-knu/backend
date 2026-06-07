# ZoneSafe 위험 판단 알고리즘 요약

> 본 문서는 ZoneSafe 의 **위험 판단 로직** 전체를 한 페이지로 요약한다.
>
> - 코드: `detection/main.py`, `detection/roi_checker.py`
> - 상세 설계(운전자 식별 휴리스틱) : `detection/DRIVER_DETECTION_DESIGN.md`
> - 백엔드 연동(콜백/저장/브로드캐스트) : `ARCHITECTURE.md`, `BACKEND_SUMMARY.md`

---

## 0. 한눈 요약

```
YOLO 탐지 → 사람/지게차 분리 → ★운전자 제외(_is_riding)★
        → ROI 별 (내부 contains | 외곽 거리 ≤ threshold) 검사
        → 시나리오 매핑(작업자 단독/지게차 동시/지게차 단독)
        → 알람 후보 생성 → 쿨다운 필터 → 백엔드 콜백
```

- 입력: YOLO 추론 결과 `detections = [{trackId,label,bbox,conf}, ...]`
- 알람: `(roiId, type, severity, trackId, label, bbox)` 단위로 산출
- 외부 채널: `POST /api/v1/internal/alarms` (저장 + STOMP 브로드캐스트)
- 동작 단위: 매 `--skip-frames` 프레임마다 1회 판정 (기본 5프레임)

---

## 1. 알고리즘 입력 / 출력 데이터 구조

### 1.1 입력 — Detection (한 프레임)

```python
detections: list[dict] = [
    {
      "trackId": int,    # YOLO tracker(persist=True) 가 부여한 고유 ID
      "label":   str,    # "person" | "forklift"
      "bbox":    [x1, y1, x2, y2],   # 좌상단~우하단 픽셀 좌표
      "confidence": float            # 0.0 ~ 1.0
    },
    ...
]
```

### 1.2 입력 — ROI 설정

```python
roi_configs = [
    {
      "roiId": int,
      "name": str,
      "active": bool,
      "polygon": [[x1,y1], [x2,y2], ...],  # 다각형(최소 3점)
      "dangerDistanceThreshold": int | None,
      # 명세서 §4. ROI 외곽선 기준 '근접' 픽셀 거리.
      # None이면 '내부 contains' 만으로 위험 판정.
    },
    ...
]
```

### 1.3 출력 — 알람 후보

```python
alarms = [
    {
      "roiId": int, "roiName": str,
      "type":     "WORKER_INTRUSION" | "WORKER_FORKLIFT_PROXIMITY",
      "severity": "DANGER" | "WARN",
      "trackId": int, "label": str, "bbox": [x1,y1,x2,y2]
    },
    ...
]
```

---

## 2. 메인 파이프라인 (`detection/main.py`)

매 프레임 루프에서 다음을 수행한다.

```
1. cap.read()                           # OpenCV VideoCapture 프레임 1장
2. frame_idx % skip_frames != 0 → skip   # 부하 절감(기본 5프레임당 1회)

3. (옵션) ROI 재조회                     # backend-url 모드 & roi_refresh(30s) 경과 시
   rois = client.get_rois(camera_id)
   roi_checker = RoiChecker(rois)

4. results = YOLO.track(frame, persist=True, conf=args.confidence)
   detections = build_detections(results, model)    # trackId/label/bbox/conf 정규화

5. client.send_detection_frame(cameraId, frame_ts, detections, videoTimeSec)
       → POST /api/v1/internal/detections/frame
       → 백엔드가 /topic/detections/{cameraId} 로 브로드캐스트

6. alarms = roi_checker.check_danger(detections)    # ★ 위험 판정 본체 ★

7. for alarm in alarms:
      if now - alarm_cooldowns[roiId] < alarm_cooldown:  continue  # 기본 10초
      alarm_cooldowns[roiId] = now
      client.create_alarm(cameraId, roiId, severity, type,
                          message, detectionsJson,
                          videoId, videoTimeSec)
       → POST /api/v1/internal/alarms
       → 백엔드 AlarmService.createAlarm() 호출 → MySQL 저장 + STOMP + 클립 추출
```

세부 옵션:
- `--skip-frames`(기본 5) — 1초 30fps 영상이면 초당 6회 판정
- `--alarm-cooldown`(기본 10초) — 같은 ROI에서 단시간 중복 알람 차단
- `--loop` — 파일 끝까지 분석 후 처음부터 반복(서버 자동 분석에서 사용)
- `--video-id` — 업로드 영상 분석일 때 지정, 백엔드 ±5초 클립 추출 트리거

---

## 3. ROI 위험 판정 본체 (`RoiChecker.check_danger`)

```
입력: detections (한 프레임)
1) persons   = [d for d in detections if d.label == "person"]
   forklifts = [d for d in detections if d.label == "forklift"]

2) ── 운전자(탑승자) 제외 ──
   persons = [
       p for p in persons
       if not any( _is_riding(p.bbox, f.bbox) for f in forklifts )
   ]

3) 각 ROI(active) 마다:
       persons_in   = filter_inside_or_near(persons,   use_foot=True)
       forklifts_in = filter_inside_or_near(forklifts, use_foot=False)

       if not persons_in:                  # (a) 지게차만 → 알람 없음
           continue
       if forklifts_in:                    # (b) 작업자 + 지게차
           for p in persons_in:
               alarms += [(WORKER_FORKLIFT_PROXIMITY, DANGER, p)]
       else:                               # (c) 작업자 단독
           for p in persons_in:
               alarms += [(WORKER_INTRUSION,        WARN,    p)]
4) return alarms
```

### 3.1 시나리오 매트릭스

| ROI 안의 객체 구성 | type | severity | 결과 |
|--------------------|------|----------|------|
| 작업자만 진입 | `WORKER_INTRUSION` | `WARN` | 경고 알람 |
| 작업자 + 지게차 동시 | `WORKER_FORKLIFT_PROXIMITY` | `DANGER` | 위험 알람 |
| 지게차만 진입 | — | — | 알람 미발생 |
| 둘 다 없음 | — | — | 알람 미발생 |

### 3.2 객체별 대표점

| 객체 | 대표점 | 이유 |
|------|--------|------|
| 사람 (`person`) | bbox 하단 중앙 `(cx, y2)` | 발이 바닥에 닿는 위치가 ROI 진입의 결정 좌표 |
| 지게차 (`forklift`) | bbox 정중앙 `(cx, cy)` | 차체 중심으로 진입 여부 판단 |

### 3.3 진입/근접 판정 (`_filter_inside_or_near`)

```python
pt = foot_point(bbox) if use_foot else center_point(bbox)
if polygon.contains(pt):                                       # (A) 내부
    matched.append(obj)
elif threshold and polygon.exterior.distance(pt) <= threshold: # (B) 근접
    matched.append(obj)
```

- **(A) 내부 contains** — Shapely `Polygon.contains(Point)` 로 폴리곤 내부 여부.
- **(B) 외곽 근접** — `polygon.exterior.distance(pt)` 가 임계값 이하면 "근접" 으로 위험 후보 포함.
  `dangerDistanceThreshold` 가 `None` 이면 (B) 비활성, 오직 내부 진입만 판정.

---

## 4. 운전자(탑승자) 제외 휴리스틱 (`_is_riding`)

> 본 절은 `DRIVER_DETECTION_DESIGN.md` §4–§5 의 핵심 요약. 더 깊은 설계 근거(케이스 도식, 엣지케이스, 점진 적용 전략)는 설계 문서를 참조.

### 4.1 문제

YOLO 가 지게차 운전석 안의 사람도 `person` 으로 검출하는 케이스가 있다.
"사람이 ROI 안에 있다" 규칙만 적용하면 운전자도 위험 알람으로 잡혀 false positive 가 폭증.

### 4.2 핵심 통찰 — "발 위치의 y"

- 보행자: 발(bbox y_max) 이 지게차 bbox **바닥 라인** 근처
- 운전자: 발이 지게차 bbox **상단부**(운전석 발판) 근처

→ "사람 bbox의 발 y 가 forklift bbox 어느 비율에 위치하는지" 가 결정자.

### 4.3 단일 프레임 휴리스틱

```python
def _is_riding(person_bbox, forklift_bbox, cab_floor_ratio=0.5):
    px1, py1, px2, py2 = person_bbox
    fx1, fy1, fx2, fy2 = forklift_bbox

    # (사전 필터) 지게차가 사람보다 너무 작으면 운전 불가능
    if (fy2 - fy1) < (py2 - py1) * 0.6:
        return False

    px = (px1 + px2) / 2      # 발의 x (가로 중심)
    py = py2                   # 발의 y (bbox 하단)

    # 1) 발 x 가 forklift 가로 범위 안인가
    if not (fx1 <= px <= fx2):
        return False

    # 2) 발 y 가 forklift 위쪽 cab_floor_ratio 영역인가
    cab_floor_y = fy1 + (fy2 - fy1) * cab_floor_ratio
    return py < cab_floor_y
```

### 4.4 케이스 판정표

| 케이스 | 발 x | 발 y | 판정 | 결과 |
|--------|------|------|------|------|
| 지게차 앞을 걷는 보행자 | forklift 안 | forklift 하단(바닥) | `py >= cab_floor_y` | **탑승 아님 → 알람** |
| 운전석 탑승자 | forklift 안 | forklift 상단(운전석) | `py < cab_floor_y` | **탑승 → 알람 제외** |
| 옆에 있는 사람 | forklift 밖 | — | 조건 1 실패 | **탑승 아님 → 알람** |
| 지게차가 너무 작음 | — | — | 사전 필터 | **탑승 아님** |

### 4.5 여러 forklift 가 있을 때

```python
any( _is_riding(p.bbox, f.bbox) for f in forklifts )
```

→ **OR 결합**. 하나의 forklift 라도 탑승으로 판정되면 운전자로 간주.

### 4.6 튜닝 / 점진적 적용

- `cab_floor_ratio`: 카메라 앵글 의존. 0.3 ~ 0.5 사이에서 현장 영상으로 튜닝.
- 깜빡임(YOLO 인식↔비인식 1~2프레임) 영향 시 **시간 평활**(트랙별 N프레임 다수결) 도입 가능 — 설계 문서 §7.
- 장기적으로는 `forklift_with_driver` 클래스 추가 재학습이 가장 정확.

---

## 5. 쿨다운 / 중복 억제

```python
alarm_cooldowns: dict[int, float] = {}   # key: roiId, value: 마지막 발생 epoch

if now - alarm_cooldowns.get(roiId, 0) < args.alarm_cooldown:
    continue          # 같은 ROI 에서 N초 내 추가 알람 무시 (기본 N=10)
alarm_cooldowns[roiId] = now
```

- ROI 단위로 쿨다운. trackId 단위가 아닌 이유: 같은 위험구역에서 동시 다중 알람을 사용자에게 한 건으로 알리기 위함.
- 백엔드는 들어온 알람을 **그대로 신뢰**해서 저장 (쿨다운 책임은 Python 워커).

---

## 6. ROI 동적 갱신

```
client mode + (now - last_roi_refresh > roi_refresh):
    rois = GET /api/v1/rois?cameraId=...
    roi_checker = RoiChecker(rois)
    last_roi_refresh = now
```

- 기본 30초마다 백엔드에서 ROI 목록을 다시 가져와 `RoiChecker` 재구성.
- 운영자가 UI 에서 ROI 를 추가/수정/토글하면 실시간(최대 30초) 반영.
- `active=False` 인 ROI 는 `RoiChecker.__init__` 단계에서 즉시 제외.

---

## 7. 백엔드 인계 (`api_client.BackendClient`)

| 호출 | 백엔드 엔드포인트 | 페이로드 | 역할 |
|------|-------------------|-----------|------|
| `get_rois(cameraId)` | `GET /api/v1/rois?cameraId=` | — | 활성 ROI 폴리곤·임계·규칙 가져오기 |
| `send_detection_frame(...)` | `POST /api/v1/internal/detections/frame` | `DetectionFrame{cameraId, frameTs, videoTimeSec, objects[]}` | STOMP `/topic/detections/{id}` 푸시 |
| `create_alarm(...)` | `POST /api/v1/internal/alarms` | `AlarmCreateRequest{ cameraId, roiId, severity, type, message, detectionsJson, videoId?, videoTimeSec? }` | MySQL 저장 + STOMP + ±5초 클립 추출 |

videoId/videoTimeSec 가 함께 들어오면 백엔드가
**비동기 ffmpeg ±5초 cut** 을 실행하고 알람에 `clipId` 를 attach 한다.

---

## 8. 한 줄 요약

> **YOLO 탐지 → 운전자 제외 휴리스틱(_is_riding) → ROI 폴리곤 내부 contains 또는 외곽선 근접 거리 → 작업자/지게차 조합에 따라 WARN/DANGER 분기 → 쿨다운 후 백엔드 콜백** — 이것이 ZoneSafe 의 위험 판단 알고리즘 전부이다.