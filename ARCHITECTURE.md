# ZoneSafe 백엔드 동작 구조도

> 본 문서는 `ZONESAFE_API_명세서.md`(엔드포인트/페이로드)와
> `detection/DRIVER_DETECTION_DESIGN.md`(위험 판단·운전자 식별 로직)을 토대로
> 현재 백엔드 코드(`src/main/java/.../zonesafe_be/`)가 실제로 어떻게 맞물려
> 동작하는지를 한눈에 보여주기 위해 작성되었다.

---

## 1. 전체 구성도 (High-Level)

```
                ┌──────────────────────────────────────────────┐
                │  Web Dashboard / Mobile (프론트엔드)          │
                │   - REST 호출 (axios 등)                      │
                │   - STOMP/WebSocket 구독                      │
                └─────────────┬───────────────────▲────────────┘
                              │ HTTPS              │  wss://
                              │ /api/v1/**         │  /ws
                              ▼                    │
   ┌────────────────────────────────────────────────────────────┐
   │              Spring Boot Application                       │
   │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
   │  │ Controller   │  │ Service      │  │ Repository (JPA) │  │
   │  │  (REST/WS)   │─▶│  (비즈니스)   │─▶│  (MySQL CRUD)    │  │
   │  └──────────────┘  └──────┬───────┘  └─────────┬────────┘  │
   │         ▲                 │                    │           │
   │         │                 │                    │           │
   │         │       ┌─────────┴────────┐           │           │
   │         │       │ EventPublisher   │           │           │
   │         │       │ (STOMP 브로드캐스트)│          │           │
   │         │       └─────────┬────────┘           │           │
   │         │                 │                    │           │
   │  ┌──────┴───────┐         │              ┌─────▼────────┐  │
   │  │ Internal API │         │              │  MySQL DB    │  │
   │  │ (Python ←→ BE)│         │              │  (영구 저장)  │  │
   │  └──────▲───────┘         │              └──────────────┘  │
   │         │                 ▼                                │
   └─────────┼─────── ProcessBuilder ───────────────────────────┘
             │       (Java → Python 실행)
             │
   ┌─────────┴────────────────────────────────────────┐
   │  Detection 워커 (Python · YOLO)                   │
   │   detection/main.py                              │
   │    ├─ ultralytics.YOLO(best.pt)                  │
   │    ├─ RoiChecker (roi_checker.py)                │
   │    │   └─ ROI 폴리곤 + 운전자/보행자 식별 로직     │
   │    └─ BackendClient (api_client.py)              │
   │        ├─ GET  /rois                             │
   │        ├─ POST /internal/detections/frame        │
   │        └─ POST /internal/alarms                  │
   └──────────────────────────────────────────────────┘

   ┌──────────────────────────────┐    ┌──────────────────────────┐
   │  Redis (실시간 스냅샷)          │    │  ffmpeg / ffprobe        │
   │  key: camera:{id}:latest_…    │    │  - 영상 코덱 변환(H.264)  │
   │  - DetectionService 가 read   │    │  - ±5초 클립 추출          │
   └──────────────────────────────┘    └──────────────────────────┘
```

핵심 외부 의존성:
- **MySQL** — Camera / Roi / Alarm / Clip / Video / VideoAnalysisJob / VideoAnalysisEvent 영구 저장
- **Redis** — `DetectionService` 가 최신 탐지 스냅샷을 0.001초 단위로 읽기 위함
  (`camera:{cameraId}:latest_detection` 키, AI 파트가 Write / BE가 Read)
- **WebSocket(STOMP)** — `/topic/alarms`, `/topic/alarms/camera/{id}`, `/topic/detections/{id}`, `/topic/alarms/status`
- **ffmpeg / ffprobe** — 업로드 영상 H.264 보정, 알람 시점 ±5초 클립 추출
- **Python (YOLO + Shapely)** — Spring 이 `ProcessBuilder` 로 직접 기동·관리

---

## 2. 패키지·계층 구조

```
me.zonesafe.zonesafe_be
├── controller/                     ← REST/WS 진입점
│   ├── AlarmController             /api/v1/alarms           (외부용)
│   ├── CameraController            /api/v1/cameras
│   ├── RoiController               /api/v1/rois
│   ├── ClipController              /api/v1/clips
│   ├── DetectionController         /api/v1/detections       (Redis 스냅샷)
│   ├── ModelController             /api/v1/models
│   ├── VideoController             /api/v1/videos           (업로드·분석·이벤트)
│   ├── AlarmWebSocketController    /app/ack                 (STOMP ACK 수신)
│   ├── InternalAlarmController     /api/v1/internal/alarms          ◀ Python 콜백
│   ├── InternalDetectionController /api/v1/internal/detections      ◀ Python 콜백
│   └── InternalCameraController    /api/v1/internal/cameras         ◀ Python 콜백
│
├── service/                        ← 비즈니스 로직 + 이벤트 발행 + 외부 프로세스
│   ├── AlarmService                알람 생성/조회/ACK·Resolve/Bulk-ACK
│   ├── AlarmEventPublisher         STOMP /topic/alarms* 브로드캐스트
│   ├── AlarmClipAttacher           비동기 클립 attach + 상태 이벤트 재발행
│   ├── DetectionService            Redis → DetectionSnapshot 변환
│   ├── DetectionEventPublisher     STOMP /topic/detections/{id} 브로드캐스트
│   ├── CameraService / RoiService  CRUD
│   ├── CameraEventPublisher        STOMP /topic/cameras/{id}/status
│   ├── ClipService                 ffmpeg 기반 ±5초 클립 추출/저장/다운로드
│   ├── VideoService                업로드(H.264 ensure) + 자동 분석 트리거
│   ├── VideoAnalysisService        Python main.py ProcessBuilder 실행/감시
│   └── ModelService
│
├── repository/                     ← Spring Data JPA
├── domain/                         ← @Entity (Alarm, Camera, Roi, Clip, Video …)
├── dto/                            ← Request/Response/Event 페이로드
├── enums/                          ← Severity, AlarmType, AlarmRule, VideoStatus …
└── config/                         ← WebSocketConfig, CorsConfig,
                                      GlobalResponseAdvice, GlobalExceptionHandler
```

`internal/*` 컨트롤러는 외부에 노출하지 않는 **AI 워커 ↔ 백엔드 콜백 채널**이다.
Python 워커가 알람·프레임을 백엔드로 푸시하기 위한 단방향 진입점이다.

---

## 3. 위험 판단 파이프라인 — 가장 중요한 흐름

API 명세 §5(알람) / §7(실시간 탐지) / §11(WebSocket) + 운전자 식별 설계가
실제 코드에서 어떻게 이어지는지를 시퀀스 형태로 표현한 것.

```
[Python 워커]                       [Spring Boot]                       [DB/브로커]
detection/main.py                  Controller / Service                 MySQL / Redis / STOMP
─────────────                      ──────────────                       ──────────────────

1) cv2.VideoCapture(source)
   for frame in video:
       results = YOLO.track(frame)
       detections = [{trackId,label,bbox,conf} ...]

2) RoiChecker.check_danger(detections)
   ├─ persons / forklifts 분리
   ├─ ★ DRIVER_DETECTION_DESIGN.md 의
   │   _is_riding() 로 운전자 제외
   │   - 발(foot)점이 forklift bbox
   │     상단 cab_floor_ratio 안쪽이면
   │     "탑승자" 로 판단해 persons에서 제거
   ├─ Shapely Polygon.contains / .exterior.distance
   │   로 ROI 진입·근접 판정
   └─ alarmRule 적용
       · WORKER_ONLY                   → DANGER WORKER_INTRUSION
       · WORKER_ALONE_OR_INTERACTION   → forklift 동시존재면 DANGER
                                         아니면 WARN WORKER_INTRUSION
       · ANY_OBJECT                    → WARN

3) BackendClient.send_detection_frame()
   POST /api/v1/internal/detections/frame   ───────▶  InternalDetectionController
                                                       └─ DetectionEventPublisher
                                                            └─ STOMP convertAndSend
                                                               /topic/detections/{cameraId}
                                                                                          ───▶ 프론트(실시간 bbox 오버레이)

4) (위험 판정된 알람만, 같은 ROI는 alarm_cooldown 초 동안 1건)
   BackendClient.create_alarm()
   POST /api/v1/internal/alarms             ───────▶  InternalAlarmController
                                                       └─ AlarmService.createAlarm()
                                                          ├─ Alarm 엔티티 저장(JPA)        ───▶ MySQL : alarm
                                                          ├─ AlarmEventPublisher.publish()
                                                          │     STOMP /topic/alarms
                                                          │     STOMP /topic/alarms/camera/{id} ───▶ 프론트 알람 토스트
                                                          │
                                                          └─ videoId & videoTimeSec 있으면
                                                             clipExtractionExecutor 에 비동기 제출
                                                                ├─ ClipService.extractAndSaveClip()
                                                                │     ffmpeg ±5초 cut          ───▶ storage/clips/*.mp4
                                                                │     Clip 저장                ───▶ MySQL : clip
                                                                └─ AlarmClipAttacher.attach()
                                                                      alarm.clipId 갱신 + 상태 이벤트 재발행
                                                                      STOMP /topic/alarms/status ───▶ 프론트 클립 링크 활성화
```

요점:
- **위험 판단 로직 자체는 Python 측**(`roi_checker.py`)에서 끝난다.
  Java 백엔드는 결과를 받아 **영속화 + 브로드캐스트 + 클립 추출**을 책임진다.
- **운전자 제외 로직**(DRIVER_DETECTION_DESIGN.md §4–5)은 알람의 false positive를
  줄이기 위해 `RoiChecker._is_riding()` 단계에서 perform 되도록 설계되어 있으므로,
  백엔드는 이미 “탑승자 제외된” 알람만 받는다고 가정해도 무방하다.
- 알람 cooldown(`--alarm-cooldown`, 기본 10초)도 Python 측 책임. 백엔드는 들어온
  알람을 그대로 신뢰하고 저장·발행한다.

---

## 4. 영상 업로드 → 자동 분석 → 이벤트/클립 흐름 (§15)

API 명세 §15(`/videos`)에 해당. `VideoService` ↔ `VideoAnalysisService` ↔ Python
워커가 어떻게 자동으로 맞물리는지의 전 과정.

```
[Frontend]                  [Spring Boot]                                       [OS Process]
──────────                  ──────────────                                      ───────────

POST /api/v1/videos/upload
   (multipart, cameraContext=1)
        │
        ▼
VideoController.uploadVideo()
        │
        ▼
VideoService.uploadVideo()
   ├─ validateFile()                  (mp4 확장자/비어있음 검사)
   ├─ saveToStorage()                 → storage/videos/{uuid}.mp4
   ├─ ensureH264()                    → ffprobe 로 코덱 확인,
   │                                    h264 아니면 ffmpeg 재인코딩 (libx264 + faststart)
   ├─ Video 엔티티 INSERT (status=UPLOADED)         ─────────────▶ MySQL : video
   └─ cameraContext != null 이면
        VideoAnalysisService.startAnalysis(videoId, null)
                   │
                   ▼
        ┌─────────────────────────────────────────────────────────┐
        │ VideoAnalysisService                                    │
        │ ├─ VideoAnalysisJob INSERT (status=QUEUED) ─────────────│─▶ MySQL : video_analysis_job
        │ └─ detectionExecutor.submit(runDetectionScript)         │
        │        │                                                │
        │        ▼  (별도 스레드)                                  │
        │     ProcessBuilder.start() ─────────────────────────────│─▶  python -u detection/main.py
        │        │                                                │       --source <video>
        │        │                                                │       --camera-id <cameraContext>
        │        │                                                │       --video-id <videoId>
        │        │                                                │       --backend-url http://localhost:8080
        │        │                                                │       --model best.pt --loop
        │        │                                                │
        │        ├─ job.status=RUNNING / startedAt 기록          │
        │        ├─ 자식 프로세스 stdout 라인 단위 로그 수집       │
        │        │                                                │
        │        │     ◀───── (3·4 단계와 동일한 콜백 시퀀스가 반복 발생) ─────
        │        │     /internal/detections/frame  : 프레임 단위 푸시
        │        │     /internal/alarms            : 위험 발생 시 푸시
        │        │
        │        └─ process.waitFor() 후
        │             job.status = COMPLETED / FAILED            │─▶ MySQL : video_analysis_job
        └─────────────────────────────────────────────────────────┘
```

### 4.1 알람 ↔ 비디오 이벤트 ↔ 클립 연결

업로드 영상 분석 중 발생한 알람은 (`videoId`, `videoTimeSec`) 메타를 함께 받는다.
이를 기반으로 `AlarmService.createAlarm()` 이 비동기로 ±5초 클립을 추출한다.

```
AlarmCreateRequest { videoId, videoTimeSec }
            │
            ▼
clipExtractionExecutor (single thread)
   ClipService.extractAndSaveClip()
      ├─ Video.filePath 로부터 ffmpeg 입력 경로 해석
      ├─ startSec = max(0, videoTimeSec - 5)
      ├─ endSec   = min(duration, videoTimeSec + 5)
      ├─ ffmpeg -ss start -i in -t dur -c:v libx264 ...   → storage/clips/{uuid}.mp4
      └─ Clip 엔티티 저장 (alarmId, cameraId, file/size/format/start/end)
            │
            ▼
AlarmClipAttacher.attach(alarmId, clipId)
   ├─ alarm.clipId = clipId  (별도 @Transactional 컴포넌트 — self-invocation 회피 목적)
   └─ AlarmEventPublisher.publishStatusChange(... clipId ...)
            │
            ▼
   STOMP /topic/alarms/status   ──▶ 프론트가 알람 카드에 클립 재생 버튼 노출
```

### 4.2 서버 재기동 시 자동 분석

`VideoAnalysisService.autoStartOnBoot()` (`@PostConstruct`) 가 기동 시 `cameraContext` 가
설정된 모든 영상에 대해 `startAnalysis()` 를 다시 호출한다. → 분석 중 서버가 죽어도
재시작만 하면 끊긴 영상 분석이 자동으로 이어진다.

---

## 5. 실시간 탐지 스냅샷 흐름 (§7)

명세서 §7의 “AI 엔진은 1초에 30번 Redis 에 덮어쓰고, 프론트는 1초에 5번
스프링을 찌른다” 부분을 코드로 옮긴 형태.

```
[AI Engine (외부)]                      [Spring Boot]                          [Frontend]
─────────────                          ──────────────                          ───────

매 프레임:
SET camera:{id}:latest_detection
     <JSON DetectionSnapshot>
        │
        ▼
    (Redis)
        ▲
        │ GET                                  GET /api/v1/detections/latest?cameraId=1
        │                                          ▲
        │                                          │
        │                                  DetectionController
        │                                          ▼
        └────────── StringRedisTemplate ◀── DetectionService.getDetectionSnapshot()
                                                  │
                                                  └─ ObjectMapper.readValue →
                                                     DetectionSnapshotResponseDto ────▶ 200 OK
```

※ Push 채널(`/topic/detections/{id}`) 은 Python 워커가
   `POST /internal/detections/frame` 로 보낸 데이터를 그대로 브로드캐스트하는
   별개의 경로이다. (Pull = Redis, Push = STOMP)

---

## 6. ROI 관리와 위험 거리(§4 ↔ DRIVER_DETECTION_DESIGN.md)

- 프론트는 `POST /api/v1/rois` 로 폴리곤·`alarmRule`·`dangerDistanceThreshold` 를 저장한다.
- Python 워커는 시작 시 `GET /api/v1/rois?cameraId=...` 로 ROI 리스트를 가져온다.
- 이후 `--roi-refresh` 초마다 다시 ROI 를 fetch하여 실시간으로 ROI 변경 반영.
- `RoiChecker` 는 Shapely `Polygon.contains(foot_point)` 으로 진입 판단을 하고,
  `dangerDistanceThreshold` 가 있을 때는 `polygon.exterior.distance(pt) <= threshold`
  로 “근접” 도 위험으로 간주한다.
- 운전자 식별 휴리스틱(설계 문서 §5)은 `_is_riding()` 으로 `check_danger` 진입부에
  적용하도록 설계되어 있으며, 단일 프레임 → 시간 평활 → 모델 재학습 3단계 점진적
  적용 전략이 명시되어 있다.

요약하면 BE 가 관리하는 ROI 메타데이터(폴리곤·규칙·임계)와 Python 의 ROI/운전자 판정
휴리스틱이 합쳐져 비로소 한 건의 알람이 만들어진다.

---

## 7. WebSocket 토픽 매핑 (§11 ↔ 코드)

| 토픽 (명세서)                          | 발행 코드                                          | 트리거                                    |
| ---------------------------------- | ---------------------------------------------- | -------------------------------------- |
| `/topic/alarms`                    | `AlarmEventPublisher.publish()`                | `AlarmService.createAlarm()`           |
| `/topic/alarms/camera/{cameraId}`  | `AlarmEventPublisher.publish()`                | `AlarmService.createAlarm()`           |
| `/topic/alarms/status`             | `AlarmEventPublisher.publishStatusChange()`    | ACK / Resolve / Bulk-ACK / Clip attach |
| `/topic/detections/{cameraId}`     | `DetectionEventPublisher.publishFrame()`       | `InternalDetectionController`          |
| `/topic/cameras/{cameraId}/status` | `CameraEventPublisher`                         | 카메라 ONLINE/OFFLINE 전환 시                |

WebSocket 설정은 `WebSocketConfig` 에서 `/ws` 엔드포인트 + `/topic` 단순 브로커 +
`/app` prefix 로 정의되어 있고, 클라이언트 ACK 메시지는
`AlarmWebSocketController` 의 `/app/ack` 가 받는다.

---

## 8. 설정 키 한눈에 보기 (`application-template.properties`)

```
spring.datasource.url                        MySQL 위치
spring.data.redis.host / port                Redis 위치 (실시간 스냅샷)
clips.storage.base-path  = ./storage/clips   ClipService 가 ffmpeg 산출물 저장
videos.storage.base-path = ./storage/videos  VideoService 가 업로드 원본 저장
spring.servlet.multipart.max-file-size=500MB 업로드 한도 (명세서 §15.1)
detection.python-path    = python            VideoAnalysisService.ProcessBuilder
detection.script-dir     = ./detection       main.py / roi_checker.py / api_client.py 위치
detection.model-path     = ./detection/best.pt   YOLO 가중치
```

이 키들이 `VideoAnalysisService.runDetectionScript()` 에서 그대로
ProcessBuilder 인자(`--source`, `--model`, `--backend-url=http://localhost:8080`)
로 조립된다.

---

## 9. 한 줄 요약

> **프론트 ↔ 외부 API (`/api/v1`)** 는 안전관리자 UI 용,
> **AI 워커 ↔ 내부 API (`/api/v1/internal`)** 는 위험 판단 결과 푸시용,
> **Redis** 는 0.001 초급 실시간 스냅샷 채널,
> **STOMP `/topic`** 은 알람·상태·탐지 프레임 브로드캐스트 채널이며,
> 위험 판단 자체(운전자 제외 포함)는 Python `RoiChecker` 가, 영속화·클립·브로드캐스트는
> Spring `AlarmService` + `ClipService` + `*EventPublisher` 가 책임진다.