# ZoneSafe 백엔드 개발 요약

> 본 문서는 ZoneSafe 백엔드(`src/main/java/me/zonesafe/zonesafe_be`)에서
> 진행한 **코드 · 알고리즘 · 데이터 구조** 를 한눈에 정리한 문서이다.
>
> 자세한 동작 흐름(시퀀스 다이어그램 등) 은 `ARCHITECTURE.md` 를,
> API 페이로드 명세는 `ZONESAFE_API_명세서.md` 를 참고할 것.

---

## 0. 한눈 요약

- **언어/프레임워크**: Java 17 + Spring Boot 3.x (Spring Web · Spring Data JPA · Spring WebSocket-STOMP)
- **DB**: MySQL (JPA · Specification 기반 동적 쿼리)
- **캐시/실시간 스냅샷**: Redis (StringRedisTemplate)
- **외부 프로세스**: ffmpeg / ffprobe (영상 H.264 보정, ±5초 클립 추출)
- **AI 워커**: Python YOLO (Spring이 `ProcessBuilder` 로 직접 기동)
- **실시간 채널**:
  - **Push (WebSocket/STOMP)** — 알람, 알람 상태, 탐지 프레임, 카메라 상태
  - **Pull (Redis)** — 카메라별 최신 탐지 스냅샷 (`camera:{id}:latest_detection`)
- **외부 API**: `/api/v1/**` (프론트엔드용)
- **내부 콜백 API**: `/api/v1/internal/**` (Python AI 워커 → BE 푸시 전용)

---

## 1. 패키지 / 계층 구조

```
me.zonesafe.zonesafe_be
├── controller/          REST · STOMP 진입점 (외부 + Internal)
├── service/             비즈니스 로직 + EventPublisher + 외부 프로세스 호출
├── repository/          Spring Data JPA (+ Specification)
├── domain/              @Entity (Alarm, Camera, Roi, Clip, Video …)
├── dto/                 Request / Response / Event payload
├── enums/               AlarmSeverity, AlarmStatus, AlarmType, VideoStatus …
└── config/              WebSocket / CORS / Jackson / GlobalException / GlobalResponseAdvice
```

| 계층 | 책임 | 핵심 클래스 |
|------|------|--------------|
| Controller | HTTP/WS 진입점, DTO 검증, ResponseEntity 조립 | `AlarmController`, `VideoController`, `ClipController`, `RoiController`, `DetectionController`, `CameraController`, `InternalAlarmController`, `InternalDetectionController`, `InternalCameraController`, `AlarmWebSocketController` |
| Service | 트랜잭션 경계, 비즈니스 로직, ffmpeg/Python 프로세스 호출, STOMP 발행 | `AlarmService`, `ClipService`, `VideoService`, `VideoAnalysisService`, `RoiService`, `CameraService`, `DetectionService`, `*EventPublisher`, `AlarmClipAttacher` |
| Repository | JPA CRUD, Specification, JPQL `@Query` | `AlarmRepository`, `VideoRepository`, `ClipRepository`, `RoiRepository`, … |
| Domain | DB Entity, `@PrePersist` 기본값 세팅 | `Alarm`, `Camera`, `Roi`, `Clip`, `Video`, `VideoAnalysisJob`, `VideoAnalysisEvent`, `Model`, `AutoLabelJob` |

---

## 2. 데이터 구조 (Entity / 테이블)

> JPA Entity 단위로 작성. 컬럼 명세는 클래스 `@Column` 정의를 그대로 옮긴 형태.

### 2.1 Alarm — 위험 알람 (`alarms`)

| 컬럼 | 타입 | 비고 |
|------|------|------|
| `alarmId` (PK) | `BIGINT AUTO_INCREMENT` | |
| `camera_id` (FK) | `Camera` `@ManyToOne LAZY` | NOT NULL |
| `roiId` | `BIGINT` | nullable, FK 객체참조는 안 함 |
| `severity` | `enum AlarmSeverity` | `INFO / WARN / DANGER` |
| `type` | `enum AlarmType` | `WORKER_INTRUSION / WORKER_FORKLIFT_PROXIMITY / UNKNOWN_OBJECT` |
| `status` | `enum AlarmStatus` | `NEW / ACK / RESOLVED` |
| `message` | `VARCHAR` | |
| `detectionsJson` | `JSON` | 알람 시점 탐지 객체 리스트(JSON String) |
| `clipId` | `BIGINT` | nullable — 비동기 클립 추출 후 갱신 |
| `snapshotUrl` | `VARCHAR(500)` | |
| `comment` | `VARCHAR(500)` | ACK/Resolve 시 코멘트 |
| `occurredAt` | `TIMESTAMP` | NOT NULL |

- 비즈니스 메서드: `updateStatus(newStatus, comment)`
- Camera 와 `@ManyToOne` (Camera ↔ Alarm 양방향)

### 2.2 Camera — 카메라 (`cameras`)

| 컬럼 | 타입 | 비고 |
|------|------|------|
| `cameraId` (PK) | `BIGINT AUTO_INCREMENT` | |
| `name` | `VARCHAR` | NOT NULL |
| `rtspUrl` | `VARCHAR` | NOT NULL |
| `siteId` / `siteName` | `BIGINT` / `VARCHAR` | NOT NULL |
| `resolution` / `fps` | `VARCHAR` / `INT` | nullable |
| `status` | `enum CameraStatus` | `ONLINE / OFFLINE` |
| `lastHeartbeat` | `TIMESTAMP` | nullable |
| `alarms` | `@OneToMany` | mappedBy="camera", cascade=ALL, orphanRemoval=true |

### 2.3 Roi — 위험구역 폴리곤 (`rois`)

| 컬럼 | 타입 | 비고 |
|------|------|------|
| `roiId` (PK) | `BIGINT AUTO_INCREMENT` | |
| `camera_id` (FK) | `Camera` `@ManyToOne LAZY` | NOT NULL |
| `name` | `VARCHAR` | NOT NULL |
| `polygonJson` | `JSON` | `int[][]` (다각형 꼭짓점) 직렬화 |
| `alarmRule` | `enum AlarmRule` | `WORKER_ONLY / WORKER_ALONE_OR_INTERACTION / ANY_OBJECT` |
| `muteForkliftOnly` | `BOOL` | NOT NULL (기본 true) |
| `dangerDistanceThreshold` | `INT` | nullable, 근접거리(px) |
| `active` | `BOOL` | NOT NULL (기본 true) |
| `createdAt` | `TIMESTAMP` | `@PrePersist` |

### 2.4 Video — 업로드 영상 (`videos`)

| 컬럼 | 타입 | 비고 |
|------|------|------|
| `videoId` (PK) | `BIGINT AUTO_INCREMENT` | |
| `filename` / `displayName` | `VARCHAR` | 원본명 / 표시명 |
| `filePath` | `VARCHAR` | 스토리지 상대경로 |
| `thumbnailPath` | `VARCHAR` | nullable |
| `fileSize` | `BIGINT` | NOT NULL |
| `duration` / `resolution` / `fps` | `INT` / `VARCHAR` / `INT` | nullable |
| `status` | `enum VideoStatus` | `UPLOADED / PROCESSING / ANALYZED / FAILED` |
| `siteId` / `cameraContext` | `BIGINT` | cameraContext = 분석 대상 카메라 ID |
| `description` | `VARCHAR(1000)` | |
| `uploadedBy` | `VARCHAR` | NOT NULL |
| `uploadedAt` / `lastAnalyzedAt` | `TIMESTAMP` | `@PrePersist` 기본값 |

### 2.5 Clip — 알람 ±5초 클립 (`clips`)

| 컬럼 | 타입 | 비고 |
|------|------|------|
| `clipId` (PK) | `BIGINT AUTO_INCREMENT` | |
| `camera_id` (FK) | `Camera` | NOT NULL |
| `alarmId` | `BIGINT` | NOT NULL (역참조용) |
| `duration` | `INT` | 클립 길이(초) |
| `fileSize` / `format` | `BIGINT` / `VARCHAR` | `mp4` 고정 |
| `filePath` / `thumbnailPath` | `VARCHAR` | |
| `occurredAt` / `startAt` / `endAt` | `TIMESTAMP` | NOT NULL |

### 2.6 VideoAnalysisJob — 분석 작업 (`video_analysis_jobs`)

| 컬럼 | 타입 | 비고 |
|------|------|------|
| `jobId` (PK) | `VARCHAR(64)` | `anlz-xxxx-xxxx` 형태 |
| `video_id` (FK) | `Video` | NOT NULL |
| `modelId` | `BIGINT` | nullable |
| `status` | `enum AnalysisJobStatus` | `QUEUED / RUNNING / COMPLETED / FAILED` |
| `progress` | `DOUBLE` | 0.0 ~ 1.0 |
| `framesProcessed` / `totalFrames` / `eventsDetected` | `INT` | |
| `resultJson` | `JSON` | 분석 결과 요약 |
| `startedAt` / `completedAt` / `estimatedRemainingSec` | `TIMESTAMP` / `INT` | |
| `createdAt` | `TIMESTAMP` | `@PrePersist` |

### 2.7 VideoAnalysisEvent — 분석 도중 발생 이벤트 (`video_analysis_events`)

| 컬럼 | 타입 | 비고 |
|------|------|------|
| `eventId` (PK) | `BIGINT AUTO_INCREMENT` | |
| `video_id` (FK) | `Video` | |
| `jobId` | `VARCHAR(64)` | |
| `frameTimestamp` | `DOUBLE` | 영상 내 초 단위 시점 |
| `severity` / `type` | `enum` | |
| `roiId` / `clipId` | `BIGINT` | nullable |
| `detectionsJson` | `JSON` | |
| `occurredAt` | `TIMESTAMP` | |

### 2.8 Model — 학습 모델 메타 (`models`)

`modelId`, `name`, `version`, `format(PYTORCH/ONNX/TENSORRT)`,
`mAP50`, `mAP50_95`, `fps`, `active`, `createdAt`

### 2.9 AutoLabelJob — 자동 라벨링 작업 (`auto_label_jobs`)

`jobId`, `modelId`, `imageSetId`, `confidenceThreshold`,
`status(AutoLabelJobStatus)`, `progress`, `totalImages`, `processed`, `createdAt`

### 2.10 Embedded — Detection

```java
@Embeddable
class Detection {
    String label;
    Long   trackId;
    String bbox;        // 예: "[340,210,420,470]"
    Double confidence;
}
```

### 2.11 Enum 카탈로그

| Enum | 값 |
|------|------|
| `AlarmSeverity` | `INFO`, `WARN`, `DANGER` |
| `AlarmStatus` | `NEW`, `ACK`, `RESOLVED` |
| `AlarmType` | `WORKER_INTRUSION`, `WORKER_FORKLIFT_PROXIMITY`, `UNKNOWN_OBJECT` |
| `AlarmRule` | `WORKER_ONLY`, `WORKER_ALONE_OR_INTERACTION`, `ANY_OBJECT` |
| `CameraStatus` | `ONLINE`, `OFFLINE` |
| `VideoStatus` | `UPLOADED`, `PROCESSING`, `ANALYZED`, `FAILED` |
| `AnalysisJobStatus` | `QUEUED`, `RUNNING`, `COMPLETED`, `FAILED` |
| `AutoLabelJobStatus` | `(상태 머신)` |
| `ModelFormat` | `PYTORCH("PyTorch")`, `ONNX("ONNX")`, `TENSORRT("TensorRT")` — Jackson `@JsonValue/@JsonCreator` 로 라벨 직렬화 |

### 2.12 ERD (관계 요약)

```
Camera (1) ─< (N) Alarm
Camera (1) ─< (N) Roi
Camera (1) ─< (N) Clip
Video  (1) ─< (N) VideoAnalysisJob
Video  (1) ─< (N) VideoAnalysisEvent
Alarm  (1) ─< (0..1) Clip      (clipId, 비동기 attach)
Video  (1) ─< (0..1) Camera    (cameraContext, soft-FK)
```

---

## 3. REST · WebSocket 엔드포인트

### 3.1 외부 REST (`/api/v1/**`)

| 메서드 | 경로 | 핸들러 | 설명 |
|------|------|--------|------|
| GET | `/alarms` | `AlarmController#getAlarms` | 필터 + 페이지네이션 조회 |
| GET | `/alarms/{id}` | `getAlarmDetail` | 단건 |
| PATCH | `/alarms/{id}/ack` | `ackAlarm` | 상태 → ACK |
| PATCH | `/alarms/{id}/resolve` | `resolveAlarm` | 상태 → RESOLVED |
| POST | `/alarms/bulk-ack` | `bulkAckAlarms` | 일괄 ACK |
| GET | `/cameras` `…/{id}` `…/{id}/stream` | `CameraController` | 목록/상세/HLS URL |
| POST · PATCH · DELETE | `/cameras` | | 등록/수정/삭제(연결 영상 정리 포함) |
| CRUD | `/rois` | `RoiController` | 활성화 토글 포함 |
| GET | `/detections/latest?cameraId=` | `DetectionController` | Redis 스냅샷 Pull |
| GET · CRUD · upload · stream | `/videos` | `VideoController` | Range Request 지원 스트리밍 |
| POST | `/videos/{id}/analyze` | | 분석 시작(Python ProcessBuilder) |
| GET | `/videos/{id}/analyze/jobs/{jobId}` | | 분석 진행률 |
| GET | `/videos/{id}/events` | | 영상별 이벤트 |
| GET · DELETE · stream | `/clips` | `ClipController` | Range Request 지원 |

### 3.2 Internal REST (`/api/v1/internal/**`) — AI 워커 콜백

| 메서드 | 경로 | 호출 주체 | 동작 |
|------|------|-----------|------|
| POST | `/internal/alarms` | Python `BackendClient.create_alarm()` | `AlarmService.createAlarm()` |
| POST | `/internal/detections/frame` | Python 프레임 푸시 | `DetectionEventPublisher.publishFrame()` |
| PATCH | `/internal/cameras/{id}/status` | Python heartbeat | `CameraService.updateCameraStatus()` |

### 3.3 WebSocket (STOMP)

| 구분 | 토픽/대상 | 발행/수신 코드 | 트리거 |
|------|----------|----------------|--------|
| 발행 | `/topic/alarms` | `AlarmEventPublisher.publish()` | 알람 생성 |
| 발행 | `/topic/alarms/camera/{cameraId}` | `AlarmEventPublisher.publish()` | 알람 생성 |
| 발행 | `/topic/alarms/status` | `AlarmEventPublisher.publishStatusChange()` | ACK / Resolve / Bulk-ACK / Clip attach |
| 발행 | `/topic/detections/{cameraId}` | `DetectionEventPublisher.publishFrame()` | Internal 프레임 콜백 |
| 발행 | `/topic/cameras/{cameraId}/status` | `CameraEventPublisher` | 카메라 상태 전환 |
| 수신 | `/app/ack` | `AlarmWebSocketController` | 클라이언트 ACK 메시지 |

> STOMP 설정(`WebSocketConfig`) — 엔드포인트 `/ws`, 단순 브로커 `/topic`, application prefix `/app`, `setAllowedOriginPatterns("*")`.

---

## 4. 핵심 알고리즘 / 처리 흐름

### 4.1 위험 알람 생성 (`AlarmService.createAlarm`)

```
1. cameraId 로 Camera 조회 (없으면 RuntimeException)
2. Alarm 엔티티 생성 (status = NEW, occurredAt = now if null)
3. alarmRepository.save()  ─▶ MySQL : alarm
4. AlarmEventPublisher.publish(AlarmEvent)
       └─ /topic/alarms , /topic/alarms/camera/{id}
5. videoId & videoTimeSec 가 있으면:
       clipExtractionExecutor.submit( () -> {
            Clip clip = ClipService.extractAndSaveClip(...)
            AlarmClipAttacher.attach(alarmId, clip.clipId)
       })
6. DTO 로 변환하여 반환
```

특징:
- `clipExtractionExecutor` 는 **단일 스레드 `Executors.newSingleThreadExecutor()`**.
  ffmpeg를 직렬화하여 동시 실행으로 인한 자원 충돌·과부하 방지.
- 클립 추출은 **비동기** → 알람 응답 지연 X. 결과는 `clipId` 갱신 + STOMP 재발행으로 프론트에 도달.
- `AlarmClipAttacher` 가 분리되어 있는 이유:
  `AlarmService` 내부에서 `@Transactional` 메서드를 self-invocation 하면
  AOP 프록시가 우회되어 트랜잭션이 적용되지 않음. → 별도 `@Component` 로 분리.

### 4.2 비동기 클립 추출 (`ClipService.extractAndSaveClip`)

```
videoTimeSec 를 받아
   startSec = max(0, videoTimeSec - 5)
   endSec   = min(video.duration, videoTimeSec + 5)
   durationSec = endSec - startSec   (0 이하이면 오류)

ffmpeg -y
   -ss <start>                    # seek (정확도 위해 -i 앞이 아닌 뒤에 두는 옵션도 검토 가능)
   -i <video.mp4>
   -t  <duration>
   -c:v libx264 -preset ultrafast -crf 23
   -c:a aac
   -movflags +faststart           # 스트리밍 가능하도록 moov 헤더 앞쪽 배치
   <storage/clips/{uuid}.mp4>

Clip 엔티티 저장:
   filePath       = {uuid}.mp4
   duration       = round(durationSec)
   startAt/endAt  = occurredAt ± offset
```

- `runFfmpegCut` 은 **2분 타임아웃** 후 `destroyForcibly()`, exit code ≠ 0 이면 산출물 삭제.
- `redirectErrorStream(true)` + stdout 폐기로 파이프 블로킹 방지.

### 4.3 영상 업로드 — H.264 보정 (`VideoService.uploadVideo` + `ensureH264`)

```
1. validateFile  : 빈 파일·확장자(mp4) 검증
2. saveToStorage : Files.copy(InputStream) 으로 안전 저장
3. ensureH264():
       codec = ffprobe 로 v:0 stream codec_name 추출
       if codec != "h264":
           ffmpeg -i in -c:v libx264 -preset fast -crf 23 -c:a aac -movflags +faststart  out.tmp
           Files.move(out.tmp, in, REPLACE_EXISTING)
4. Video 엔티티 저장 (status = UPLOADED)
5. cameraContext != null 이면 VideoAnalysisService.startAnalysis(videoId, null)
```

- `MultipartFile.transferTo` 가 환경별 상대경로 해석 차이가 있어
  **절대경로 + `Files.copy(InputStream)`** 로 안정화.
- 변환 실패 시 저장된 파일을 정리하여 좀비 파일 방지.
- 변환 timeout 10분.

### 4.4 영상 분석 잡 (`VideoAnalysisService.startAnalysis` + `runDetectionScript`)

```
startAnalysis(videoId, request):
   1) Video 조회
   2) VideoAnalysisJob 생성 (jobId = "anlz-xxxx-xxxx", status = QUEUED, progress = 0.0)
   3) cameraContext 가 있으면 detectionExecutor 에 runDetectionScript 제출
   4) 응답 DTO 반환

runDetectionScript:
   job.status = RUNNING / startedAt = now

   ProcessBuilder pb = new ProcessBuilder(
        pythonPath, "-u", script/main.py,
        "--source",      <video file>,
        "--camera-id",   <camera id>,
        "--video-id",    <video id>,
        "--backend-url", "http://localhost:8080",
        "--model",       <best.pt>,
        "--loop"
   )
   pb.directory(scriptDir)
   pb.redirectErrorStream(true)
   process.start()
   stdout 라인 단위 log.info("[detection] ...")

   exitCode = process.waitFor()
   job.status = (exit == 0 ? COMPLETED : FAILED)  /  completedAt = now
```

부가 흐름:
- `@PostConstruct autoStartOnBoot()` — 서버 재기동 시 `cameraContext` 가 있는 모든 영상을 자동으로 재분석.
- `detectionExecutor = Executors.newCachedThreadPool()` — 영상별 병렬 분석 허용.
- 진행률(`progress / framesProcessed`) 은 현재 stdout 파싱 없이 RUNNING/COMPLETED 상태만 기록.

### 4.5 알람 동적 검색 (`AlarmSpecification.filterAlarms`)

```java
Specification<Alarm> spec = (root, query, cb) -> {
   List<Predicate> ps = new ArrayList<>();
   if (cameraId != null) ps.add(cb.equal(root.get("camera").get("cameraId"), cameraId));
   if (roiId    != null) ps.add(cb.equal(root.get("roiId"), roiId));
   if (severity != null) ps.add(cb.equal(root.get("severity"), severity));
   if (type     != null) ps.add(cb.equal(root.get("type"), type));
   if (status   != null) ps.add(cb.equal(root.get("status"), status));
   if (from     != null) ps.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
   if (to       != null) ps.add(cb.lessThanOrEqualTo (root.get("occurredAt"), to));
   return cb.and(ps.toArray(new Predicate[0]));
};
```

- `AlarmRepository` 가 `JpaSpecificationExecutor<Alarm>` 를 상속하여 그대로 `findAll(spec, pageable)`.
- 페이징·정렬은 `@PageableDefault(size=20, sort="occurredAt", direction=DESC)`.

### 4.6 실시간 탐지 스냅샷 (`DetectionService.getDetectionSnapshot`)

```
key = "camera:" + cameraId + ":latest_detection"
json = StringRedisTemplate.opsForValue().get(key)
if (json == null || empty) return null;
return objectMapper.readValue(json, DetectionSnapshotResponseDto.class);
```

- AI 엔진이 매 프레임 Redis 에 SET. 백엔드는 GET 만 수행.
- 별도 channel: Push (`/topic/detections/{cameraId}`) vs Pull (`/detections/latest`).

### 4.7 영상/클립 스트리밍 — HTTP Range

```
Range 헤더가 없으면  : 0 ~ min(1MB, size)  → 200 OK
있으면              : 첫 번째 range start,end 파싱
                      length = min(1MB, end-start+1) → 206 PARTIAL_CONTENT
헤더에 Accept-Ranges: bytes 추가
ResourceRegion 으로 응답
```

- `STREAM_CHUNK_SIZE = 1MB` — 단일 응답 최대 크기.
- 브라우저 `<video>` 의 progressive playback 호환.

### 4.8 카메라 상태 변경 이벤트 (`CameraService.updateCameraStatus`)

```
previousStatus = camera.status
if request.status != null:    camera.status = request.status
if request.lastHeartbeat:     camera.lastHeartbeat = ...
if request.status != null && != previousStatus:
    cameraEventPublisher.publishStatus( /topic/cameras/{id}/status )
```

### 4.9 ROI 폴리곤 직렬화 (`RoiService.serializePolygon / deserializePolygon`)

- 입력 `int[][]` → `ObjectMapper.writeValueAsString` 으로 JSON 컬럼 저장.
- 조회 시 다시 `int[][]` 로 역직렬화.
- 활성화 토글: `roi.active = !roi.active`.

### 4.10 알람 ↔ 클립 자동 연결 — 사이드카 컴포넌트

`AlarmClipAttacher` (`@Component @Transactional`):
```
attach(alarmId, clipId):
    alarmRepository.findById(alarmId).ifPresent(a -> {
        a.setClipId(clipId);
        AlarmStatusChangedEvent 발행
            └─ /topic/alarms/status  (clipId 포함)
    });
```

→ 프론트는 같은 알람 카드 위에 "클립 재생" 버튼이 활성화됨.

---

## 5. 외부 시스템 / 설정

### 5.1 `application-template.properties` 핵심 키

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/zonesafe?...
spring.jpa.hibernate.ddl-auto=update

spring.data.redis.host=localhost
spring.data.redis.port=6379

clips.storage.base-path=./storage/clips
videos.storage.base-path=./storage/videos

spring.servlet.multipart.max-file-size=500MB
spring.servlet.multipart.max-request-size=500MB

detection.python-path=python
detection.script-dir=./detection
detection.model-path=./detection/best.pt
```

### 5.2 외부 프로세스 / 의존 도구

| 도구 | 용도 | 호출 위치 |
|------|------|-----------|
| `ffprobe` | 코덱명 확인 | `VideoService.probeVideoCodec` |
| `ffmpeg` | 영상 H.264 변환 (in-place) | `VideoService.ensureH264` |
| `ffmpeg` | ±5초 클립 추출 | `ClipService.runFfmpegCut` |
| `python` | YOLO 탐지 워커(`detection/main.py`) | `VideoAnalysisService.runDetectionScript` |

---

## 6. 동시성 / 비동기 처리

| 위치 | 실행자 | 이유 |
|------|--------|------|
| 클립 추출 | `AlarmService.clipExtractionExecutor = newSingleThreadExecutor()` | ffmpeg 직렬화로 부하/충돌 회피 |
| 영상 분석 | `VideoAnalysisService.detectionExecutor = newCachedThreadPool()` | 영상별 병렬 분석 허용, 유휴 스레드 회수 |
| 알람 발행 | 동기 (트랜잭션 내) | 일관성 우선 |
| Redis 스냅샷 | 동기 GET | 단순 캐시 read |

트랜잭션 전략:
- 서비스 클래스에 `@Transactional(readOnly = true)` 기본 + 변경 메서드에 `@Transactional` 재선언.
- self-invocation 회피: `AlarmClipAttacher` 분리.

---

## 7. 공통 응답 / 예외 처리

- **`ApiResponse<T>`** — `{ success, code, message, data, timestamp }` 통일 포맷.
  - `GlobalResponseAdvice` 가 컨트롤러 반환값을 자동으로 감싼다(가정).
- **`GlobalExceptionHandler`** — `IllegalArgumentException`, `RuntimeException` 등을
  HTTP 상태 코드와 `ApiResponse.error(code, message)` 로 변환.
- **`JacksonConfig`** — JavaTimeModule, ZonedDateTime ISO-8601 직렬화.
- **`CorsConfig`** — 프론트 도메인 화이트리스트.

---

## 8. 코드 ↔ 명세서 매핑 한눈에 보기

| 명세서 절 | 백엔드 구현 |
|-----------|--------------|
| §4 ROI | `RoiController` + `RoiService` + `Roi` |
| §5 알람 | `AlarmController` + `AlarmService` + `Alarm` + `AlarmEventPublisher` |
| §6 클립 | `ClipController` + `ClipService` + `Clip` (+ `AlarmClipAttacher`) |
| §7 실시간 탐지(Pull) | `DetectionController` + `DetectionService` (Redis) |
| §11 WebSocket | `WebSocketConfig` + `*EventPublisher` + `AlarmWebSocketController` |
| §15 영상 업로드/분석 | `VideoController` + `VideoService` + `VideoAnalysisService` + `Video / VideoAnalysisJob / VideoAnalysisEvent` |
| Internal 콜백 | `InternalAlarmController` / `InternalDetectionController` / `InternalCameraController` |

---

## 9. 한 줄 요약

> 백엔드는 **(a) 프론트 REST/WS 진입 + (b) AI 워커 internal 콜백 수신 + (c) Python 워커 기동/관리 + (d) ffmpeg 기반 영상·클립 변환/추출 + (e) STOMP 이벤트 브로드캐스트** 의 다섯 가지 책임을 가지며,
> JPA 엔티티(Alarm/Camera/Roi/Clip/Video/VideoAnalysisJob/Event/Model/AutoLabelJob) 와 enum 셋(Severity/Status/Type/Rule/JobStatus 등) 으로 도메인을 표현한다.