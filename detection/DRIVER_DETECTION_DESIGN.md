# 지게차 운전자 vs 보행자 구분 설계

> 위험구역 알람의 false positive를 줄이기 위한 휴리스틱 설계 문서.
> YOLO 모델이 지게차 안의 사람을 인식하는 경우, 운전석 탑승자는 위험 알람 대상에서 제외해야 한다.

---

## 1. 문제 정의

- YOLO 모델이 지게차 내부의 사람을 **인식하는 경우와 인식하지 않는 경우가 혼재**되어 나타남
- 인식이 될 때, 지게차에 **탑승한 운전자**는 위험 알람 대상이 **아님**
- 하지만 단순히 "사람이 ROI 위험구역 안에 있다" 규칙만으로는 운전자도 알람 대상이 됨

따라서 운전자(탑승자) ↔ 보행 작업자(위험 대상)를 코드로 구분하는 base-line 로직이 필요.

---

## 2. 1차 아이디어 — 공간적 포함률 (Containment)

사람 bbox가 지게차 bbox에 충분히 포함되면 "탑승 중"으로 보고 알람 제외.

```python
def is_riding(person_bbox, forklift_bbox, overlap_ratio=0.7):
    inter = intersection_area(person_bbox, forklift_bbox)
    return inter / area(person_bbox) >= overlap_ratio
```

- 장점: 추가 학습 불필요, 결정론적, ROI 로직과 분리 가능
- 단점: **지게차 앞을 걸어가는 보행자도 2D 투영상 forklift bbox 안에 100% 포함될 수 있음** → 운전자로 오인 → 알람 미발생(위험)

---

## 3. 1차 아이디어의 결정적 결함

"앞을 걸어가는 보행자"와 "운전석 탑승자"가 2D bbox만 보면 둘 다 forklift bbox에 완전 포함되는 케이스가 발생.

```
보행자 (앞을 걸어감)           운전자 (탑승)

   ┌─────┐                    ┌─────┐
   │ 사람 │                    │ ### │ ← 운전석에 앉아있어
   │     │                    │ ### │   상체만 보임
   │     │                    └─────┘
   │     │  ← 발이 바닥         ┌─┴───┴─┐
 --+-----+--------             │ 지게차 │
   ┌─────────┐                 │       │
   │  지게차  │                 └───────┘
   └─────────┘                       ^ 발이 캐빈 위에 있음
        ^ 바닥과 같은 라인
```

→ Containment 단독으로는 두 케이스를 구분할 수 없음.

---

## 4. 핵심 통찰 — "발(foot)의 수직 위치"

운전자와 보행자는 결정적 물리 차이가 있다:

- **보행자**의 발(bbox 하단 y_max)은 지게차 bbox **바닥과 같은 라인**에 있음 (바닥에 서 있음)
- **운전자**의 발은 지게차 bbox **상단부**(운전석 발판)에 있음

즉, **사람 bbox의 발 점이 forklift bbox의 어느 높이에 위치하는지**가 결정적 판별자.

---

## 5. 개선된 휴리스틱

```python
def is_riding(person_bbox, forklift_bbox, cab_floor_ratio=0.5):
    """
    person이 forklift에 탑승 중인지 판정.

    cab_floor_ratio: forklift bbox 상단으로부터 운전석 발판으로 보는 비율.
                     0.5이면 forklift bbox의 위쪽 50%를 운전석 영역으로 간주.
    """
    px = (person_bbox[0] + person_bbox[2]) / 2  # 발의 x (bbox 가로 중심)
    py = person_bbox[3]                          # 발의 y (bbox 하단)
    fx1, fy1, fx2, fy2 = forklift_bbox

    # 1) 발이 forklift bbox 가로 범위 안에 있어야 함
    if not (fx1 <= px <= fx2):
        return False

    # 2) 발이 forklift bbox 위쪽 영역(운전석)에 있어야 함
    cab_floor_y = fy1 + (fy2 - fy1) * cab_floor_ratio
    return py < cab_floor_y
```

### 케이스별 판정표

| 케이스 | 발 x | 발 y | 판정 | 결과 |
|---|---|---|---|---|
| 지게차 앞을 걷기 | forklift 안 | forklift **하단(바닥)** | `py > cab_floor_y` | 탑승 아님 -> 알람 발생 |
| 운전석 탑승 | forklift 안 | forklift **상단(운전석)** | `py < cab_floor_y` | 탑승 -> 알람 제외 |
| 옆에 있음 | forklift 밖 | - | 첫 조건 실패 | 탑승 아님 -> 알람 발생 |

---

## 6. 남은 엣지 케이스

1. **마스트/포크에 발이 가려진 보행자**
   - bbox 하단이 잘려 위로 올라가, 운전자처럼 판정될 수 있음
   - 완화: 트랙 ID 단위 N프레임 시간 평활(예: 최근 10프레임 중 6프레임 이상 riding 판정 시 확정)

2. **카메라 앵글 의존성**
   - 위에서 비스듬히 내려보는 cctv: `cab_floor_ratio ~= 0.5`
   - 거의 수평으로 보는 카메라: `cab_floor_ratio ~= 0.3 ~ 0.4` (운전석이 더 위쪽으로 보임)
   - 영상 표본을 보고 튜닝 필요

3. **지게차 bbox가 사람보다 작은 경우**
   - `forklift_height < person_height * 0.6` 같으면 운전자 가능성 거의 없음 -> 사전 필터로 빠르게 false 반환 가능

4. **여러 forklift가 겹쳐있는 상황**
   - 어떤 forklift라도 `is_riding=True`면 탑승으로 간주(OR 결합)

---

## 7. 권장 구현 흐름

`detection/roi_checker.py`의 `check_danger()` 진입부에서 운전자 제거:

```python
def check_danger(self, detections):
    persons = [d for d in detections if d["label"] in ("person", "worker")]
    forklifts = [d for d in detections if d["label"] == "forklift"]

    # 운전자(탑승자) 제외 — 발 위치 기준
    persons = [p for p in persons
               if not any(self._is_riding(p["bbox"], f["bbox"]) for f in forklifts)]

    # 이후 기존 ROI 위험 판정 로직 (변경 없음)
    ...
```

### 점진적 적용

1. **1단계 — 단일 프레임 휴리스틱 (이번 PR)**
   - `_is_riding()`을 RoiChecker에 추가
   - `cab_floor_ratio=0.5` 기본값

2. **2단계 — 시간 평활 (필요 시)**
   - RoiChecker에 `self._riding_history: dict[trackId, deque[bool]]` 캐시
   - 최근 N프레임 중 K프레임 이상 riding이면 확정
   - YOLO 깜빡임(인식↔비인식) 1~2프레임 흡수

3. **3단계 — 모델 차원 해결 (장기)**
   - `forklift_with_driver` 클래스 추가하여 재학습
   - 비용은 크지만 가장 정확

---

## 8. 튜닝/검증 체크리스트

- [ ] 현장 영상 표본에서 `cab_floor_ratio` 0.3 / 0.4 / 0.5 / 0.6 비교
- [ ] 운전자가 위험 알람에 잡히지 않는지 확인 (recall reduction OK)
- [ ] 지게차 앞을 걷는 보행자가 여전히 잡히는지 확인 (critical — 누락되면 안 됨)
- [ ] 마스트 가림 케이스에서 거짓 운전자 판정 빈도 측정
- [ ] 필요 시 시간 평활 도입

---

## 9. 미해결 결정사항

- **임계값(cab_floor_ratio)**: 카메라 앵글에 따라 다르므로 현장 영상 보고 결정
- **시간 평활 도입 시점**: 단일 프레임 결과 보고 깜빡임이 실제로 알람 품질에 영향을 주는 경우에만 추가