# ZONESAFE API 명세서

> CNN 활용 작업자 안전관리 솔루션 - 백엔드 API 명세  
> **버전:** v1.0 / **최종 수정일:** 2026.04.26  
> **기술 스택:** Java Spring Boot + MySQL + WebSocket(STOMP)

---

## 1. 개요

### 1.1 Base URL


| 환경        | URL                                |
| --------- | ---------------------------------- |
| 개발        | `http://localhost:8080/api/v1`     |
| 운영        | `https://zonesafe.{domain}/api/v1` |
| WebSocket | `wss://zonesafe.{domain}/ws`       |


### 1.2 인증 방식

- **JWT Bearer Token** 기반 인증
- 모든 API(로그인 제외)는 HTTP Header에 `Authorization: Bearer {access_token}` 필수
- Access Token 유효기간: 1시간 / Refresh Token 유효기간: 7일

### 1.3 공통 응답 형식

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": { },
  "timestamp": "2026-04-26T14:30:00Z"
}
```

### 1.4 공통 에러 코드


| HTTP | Code                | 설명             |
| ---- | ------------------- | -------------- |
| 400  | `INVALID_PARAMETER` | 잘못된 파라미터       |
| 401  | `UNAUTHORIZED`      | 인증 실패 또는 토큰 만료 |
| 403  | `FORBIDDEN`         | 권한 없음          |
| 404  | `NOT_FOUND`         | 리소스 없음         |
| 409  | `CONFLICT`          | 중복 또는 충돌       |
| 500  | `INTERNAL_ERROR`    | 서버 내부 오류       |


### 1.5 페이징 공통 파라미터


| 파라미터   | 타입     | 기본값              | 설명             |
| ------ | ------ | ---------------- | -------------- |
| `page` | int    | 0                | 페이지 번호(0부터 시작) |
| `size` | int    | 20               | 페이지당 항목 수      |
| `sort` | string | `createdAt,desc` | 정렬 기준          |


---

## 2. 인증 API (`/auth`)

### 2.1 로그인

- **POST** `/auth/login`

**Request**

```json
{
  "username": "admin",
  "password": "P@ssw0rd!"
}
```

**Response 200**

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "eyJhbGciOi...",
    "expiresIn": 3600,
    "user": {
      "userId": 1,
      "username": "admin",
      "role": "ADMIN"
    }
  }
}
```

### 2.2 토큰 재발급

- **POST** `/auth/refresh`

```json
{ "refreshToken": "eyJhbGciOi..." }
```

### 2.3 로그아웃

- **POST** `/auth/logout` (토큰 필요)

### 2.4 현재 사용자 정보

- **GET** `/auth/me`

---

## 3. 카메라/사이트 관리 API (`/cameras`)

### 3.1 카메라 목록 조회

- **GET** `/cameras`
- **Query:** `siteId`, `status` (`ONLINE`/`OFFLINE`)

**Response 200**

```json
{
  "data": [
    {
      "cameraId": 1,
      "name": "1번 라인 입구",
      "rtspUrl": "rtsp://...",
      "siteId": 1,
      "siteName": "구미공장 A동",
      "resolution": "1920x1080",
      "fps": 30,
      "status": "ONLINE",
      "lastHeartbeat": "2026-04-26T14:29:55Z"
    }
  ]
}
```

### 3.2 카메라 상세 조회

- **GET** `/cameras/{cameraId}`

### 3.3 카메라 등록

- **POST** `/cameras` (ADMIN)

```json
{
  "name": "1번 라인 입구",
  "rtspUrl": "rtsp://192.168.0.10/stream1",
  "siteId": 1,
  "siteName": "구미공장 A동",
  "resolution": "1920x1080",
  "fps": 30
}
```

### 3.4 카메라 수정/삭제

- **PUT** `/cameras/{cameraId}`

```json
{
    "name": "수정된 테스트 카메라",
    "rtspUrl": "rtsp://test-url.com/stream1",
    "siteId": 1,
    "siteName": "테스트 현장 A",
    "resolution": "4K",
    "fps": 60,
    "status": "ONLINE"
}
```

- **DELETE** `/cameras/{cameraId}`

### 3.5 실시간 스트림 URL 발급

- **GET** `/cameras/{cameraId}/stream`
- HLS(.m3u8) URL 반환 (웹 대시보드 영상 플레이어용)

```json
{ "data": { "streamUrl": "https://.../live/cam1/index.m3u8", "type": "HLS" } }
```

---

## 4. 위험구역(ROI) 관리 API (`/rois`)

### 4.1 ROI 목록 조회

- **GET** `/rois?cameraId={cameraId}`

**Response 200**

```json
{
  "data": [
    {
      "roiId": 10,
      "cameraId": 1,
      "name": "지게차 진입구역",
      "polygon": [[120,200],[480,200],[480,520],[120,520]],
      "alarmRule": "WORKER_ALONE_OR_INTERACTION",
      "muteForkliftOnly": true,
      "dangerDistanceThreshold": 150,
      "active": true,
      "createdAt": "2026-04-01T09:10:00Z"
    }
  ]
}
```

### 4.2 ROI 생성

- **POST** `/rois`


| 필드                        | 타입      | 필수  | 설명                                                           |
| ------------------------- | ------- | --- | ------------------------------------------------------------ |
| `cameraId`                | long    | O   | 대상 카메라 ID                                                    |
| `name`                    | string  | O   | 구역명                                                          |
| `polygon`                 | int[][] | O   | 다각형 꼭짓점 좌표(픽셀 기준)                                            |
| `alarmRule`               | enum    | O   | `WORKER_ONLY` / `WORKER_ALONE_OR_INTERACTION` / `ANY_OBJECT` |
| `muteForkliftOnly`        | bool    | X   | 지게차 단독 진입 시 뮤팅 여부 (기본 `true`)                                |
| `dangerDistanceThreshold` | int     | X   | 위험 판단 거리(px) - 작업자-지게차 간                                     |
| `active`                  | bool    | X   | 활성화 여부 (기본 `true`)                                           |


```json
{
  "cameraId": 1,
  "name": "지게차 진입구역",
  "polygon": [[120,200],[480,200],[480,520],[120,520]],
  "alarmRule": "WORKER_ALONE_OR_INTERACTION",
  "muteForkliftOnly": true,
  "dangerDistanceThreshold": 150,
  "active": true
}
```

### 4.3 ROI 수정/삭제/상세

- **GET** `/rois/{roiId}`
- **PUT** `/rois/{roiId}`
- **PATCH** `/rois/{roiId}/active` — 활성화 토글
- **DELETE** `/rois/{roiId}`

---

## 5. 알람/이벤트 API (`/alarms`)

### 5.1 알람 목록 조회 (필터/페이징)

- **GET** `/alarms`

**Query Parameters**


| 파라미터                   | 타입       | 설명                                                              |
| ---------------------- | -------- | --------------------------------------------------------------- |
| `cameraId`             | long     | 카메라 필터                                                          |
| `roiId`                | long     | ROI 필터                                                          |
| `severity`             | enum     | `INFO`/`WARN`/`DANGER`                                          |
| `type`                 | enum     | `WORKER_INTRUSION`/`WORKER_FORKLIFT_PROXIMITY`/`UNKNOWN_OBJECT` |
| `status`               | enum     | `NEW`/`ACK`/`RESOLVED`                                          |
| `from`                 | datetime | 시작 시각 (ISO-8601)                                                |
| `to`                   | datetime | 종료 시각                                                           |
| `page`, `size`, `sort` | -        | 공통 페이징                                                          |


**Response 200**

```json
{
  "data": {
    "content": [
      {
        "alarmId": 1024,
        "cameraId": 1,
        "cameraName": "1번 라인 입구",
        "roiId": 10,
        "roiName": "지게차 진입구역",
        "severity": "DANGER",
        "type": "WORKER_FORKLIFT_PROXIMITY",
        "status": "NEW",
        "message": "작업자-지게차 근접 (거리 92px)",
        "detections": [
          { "label": "worker",   "trackId": 17, "bbox": [340,210,420,470], "confidence": 0.91 },
          { "label": "forklift", "trackId": 33, "bbox": [430,250,610,500], "confidence": 0.94 }
        ],
        "clipId": 5012,
        "snapshotUrl": "https://.../alarms/1024/snapshot.jpg",
        "occurredAt": "2026-04-26T14:21:33Z"
      }
    ],
    "page": 0, "size": 20, "totalElements": 137, "totalPages": 7
  }
}
```

### 5.2 알람 상세 조회

- **GET** `/alarms/{alarmId}`

### 5.3 알람 확인/해제

- **PATCH** `/alarms/{alarmId}/ack` — 확인(ACK) 처리
- **PATCH** `/alarms/{alarmId}/resolve`

```json
{ "comment": "현장 확인 완료. 작업자 안전구역으로 이동" }
```

### 5.4 알람 일괄 처리

- **POST** `/alarms/bulk-ack`

```json
{ "alarmIds": [1024, 1025, 1026] }
```

필요한 이유.
'알람 일괄 처리(Bulk Ack)' 기능이 왜 필요한가요?
"이메일함의 '전체 선택 -> 읽음 처리' 버튼과 똑같은 역할입니다."

공장 현장을 상상해 보세요. 지게차가 위험 구역에 주차를 해놓고 5분 동안 머물러 있다면, 시스템은 1초나 10초에 한 번씩 계속해서 알람을 발생시킬 수 있습니다.
안전 관리자가 모니터 앞에 앉았을 때 "1번 카메라 지게차 근접 알람"이 50개가 쌓여 있다면 어떨까요?
이걸 앞서 만든 상세 API로 하나하나 들어가서 50번을 "확인(ACK)" 누르는 것은 엄청난 고역입니다.

그래서 프론트엔드 화면에 [체크박스]를 만들고, 여러 개의 알람을 한 번에 선택한 뒤 [일괄 확인] 버튼을 누르기 위해 만든 기능입니다. 프론트엔드에서 선택한 알람들의 ID 번호표들([1024, 1025, 1026])만 배열로 묶어서 백엔드로 던져주면, 백엔드가 한 번에 상태를 바꿔주는 것이죠.

---

## 6. 비디오 클립 API (`/clips`)

> 위험 발생 시점 전후 5초(총 10초) 자동 저장된 클립을 관리합니다.

### 6.1 클립 목록 조회

- **GET** `/clips?cameraId=1&from=...&to=...`

```json
{
  "data": {
    "content": [
      {
        "clipId": 5012,
        "alarmId": 1024,
        "cameraId": 1,
        "duration": 10,
        "fileSize": 2458123,
        "format": "mp4",
        "downloadUrl": "/api/v1/clips/5012/download",
        "streamUrl": "/api/v1/clips/5012/stream",
        "thumbnailUrl": "/api/v1/clips/5012/thumbnail",
        "occurredAt": "2026-04-26T14:21:33Z",
        "startAt":   "2026-04-26T14:21:28Z",
        "endAt":     "2026-04-26T14:21:38Z"
      }
    ]
  }
}
```

### 6.2 클립 다운로드

- **GET** `/clips/{clipId}/download` → `application/octet-stream` (mp4)

### 6.3 클립 스트리밍

- **GET** `/clips/{clipId}/stream` → `video/mp4` (Range Request 지원)

### 6.4 썸네일

- **GET** `/clips/{clipId}/thumbnail` → `image/jpeg`

### 6.5 클립 삭제

- **DELETE** `/clips/{clipId}` (ADMIN)

---

## 7. 실시간 탐지 결과 API (`/detections`)

### 7.1 최근 탐지 결과 스냅샷

- **GET** `/detections/latest?cameraId={cameraId}`

```json
{
  "data": {
    "cameraId": 1,
    "frameTimestamp": "2026-04-26T14:30:00.123Z",
    "objects": [
      { "trackId": 17, "label": "worker",   "bbox": [340,210,420,470], "confidence": 0.91, "inRoi": [10] },
      { "trackId": 33, "label": "forklift", "bbox": [430,250,610,500], "confidence": 0.94, "inRoi": [10] }
    ],
    "fps": 28.4,
    "modelVersion": "yolov8m_zonesafe_v3"
  }
}
```

AI 엔진(주로 Python)이 실시간으로 탐지한 데이터를 아주 빠르게 덮어쓰고, 스프링 부트가 그걸 0.01초 만에 읽어가려면 Redis(레디스) 같은 인메모리 DB가 필수입니다.

💡 핵심 포인트 (AI 개발자와의 협업 조율)
이 코드가 완벽하게 돌아가려면, Python으로 YOLO 등을 돌리고 있는 AI 엔진 파트 담당자에게 딱 한 가지를 요청하셔야 합니다.

"초당 프레임 처리하실 때, 탐지 결과 JSON을 Redis에 저장해 주시고요.

```Key 이름은 camera:{카메라ID}:latest_detection ```형식으로 해주세요!

그리고 JSON 구조는 제가 드린 API 명세서랑 똑같이 맞춰서 String으로 넣어주시면 됩니다."

이렇게만 맞춰지면,

1. AI 엔진이 1초에 30번씩 Redis의 데이터를 덮어쓰고 (Write)

2. 프론트엔드가 1초에 5번씩 스프링 부트 API를 찌르면 (Read)

3. 스프링 부트는 DB 부하 없이 Redis에서 0.001초 만에 최신 데이터를 꺼내다 주는 완벽한 실시간 파이프라인이 완성됩니다! 🚀

---

## 8. 통계/대시보드 API (`/stats`)

### 8.1 대시보드 요약

- **GET** `/stats/summary?from=...&to=...`

```json
{
  "data": {
    "totalAlarms": 47,
    "byseverity": { "INFO": 12, "WARN": 23, "DANGER": 12 },
    "totalClips": 12,
    "activeCameras": 4,
    "totalCameras": 5,
    "avgFps": 27.8
  }
}
```

### 8.2 시계열 알람 통계

- **GET** `/stats/alarms/timeseries?interval=hour|day|week&from=...&to=...`

```json
{
  "data": [
    { "bucket": "2026-04-26T13:00:00Z", "INFO": 1, "WARN": 3, "DANGER": 2 },
    { "bucket": "2026-04-26T14:00:00Z", "INFO": 0, "WARN": 1, "DANGER": 1 }
  ]
}
```

### 8.3 카메라별 알람 통계

- **GET** `/stats/alarms/by-camera?from=...&to=...`

### 8.4 객체 클래스별 탐지 통계

- **GET** `/stats/detections/by-class?cameraId=...&from=...&to=...`

---

## 9. 모델 관리 API (`/models`) — ADMIN

### 9.1 모델 목록

- **GET** `/models`

```json
{
  "data": [
    {
      "modelId": 1,
      "name": "yolov8m_zonesafe_v3",
      "version": "v3",
      "format": "PyTorch",
      "mAP50": 0.912,
      "mAP50_95": 0.687,
      "fps": 28.4,
      "active": true,
      "createdAt": "2026-04-10T10:00:00Z"
    }
  ]
}
```

### 9.2 활성 모델 변경

- **PATCH** `/models/{modelId}/activate`

### 9.3 ONNX 변환 작업

- **POST** `/models/{modelId}/export`

```json
{ "format": "ONNX" }
```

### 9.4 오토 라벨링 작업

- **POST** `/models/auto-label`

```json
{
  "modelId": 1,
  "imageSetId": 12,
  "confidenceThreshold": 0.5
}
```

- **GET** `/models/auto-label/jobs/{jobId}` — 작업 상태 조회

```json
{
  "data": {
    "jobId": "ab12-...",
    "status": "RUNNING",
    "progress": 0.42,
    "totalImages": 1000,
    "processed": 420
  }
}
```

---

## 10. 사용자 관리 API (`/users`) — ADMIN


| Method | Path                       | 설명      |
| ------ | -------------------------- | ------- |
| GET    | `/users`                   | 사용자 목록  |
| POST   | `/users`                   | 사용자 생성  |
| GET    | `/users/{userId}`          | 상세 조회   |
| PUT    | `/users/{userId}`          | 정보 수정   |
| PATCH  | `/users/{userId}/password` | 비밀번호 변경 |
| DELETE | `/users/{userId}`          | 삭제      |


**Role:** `ADMIN` / `OPERATOR` / `VIEWER`

---

## 11. WebSocket API (실시간 채널)

### 11.1 연결

- **Endpoint:** `wss://zonesafe.{domain}/ws`
- **프로토콜:** STOMP over WebSocket (SockJS 호환)
- **인증:** 연결 시 `Authorization` 헤더에 JWT 전달

### 11.2 구독 토픽


| 토픽                                 | 설명                | 페이로드             |
| ---------------------------------- | ----------------- | ---------------- |
| `/topic/alarms`                    | 전체 알람 브로드캐스트      | `AlarmEvent`     |
| `/topic/alarms/camera/{cameraId}`  | 카메라별 알람           | `AlarmEvent`     |
| `/topic/detections/{cameraId}`     | 실시간 탐지 결과(프레임 단위) | `DetectionFrame` |
| `/topic/cameras/{cameraId}/status` | 카메라 상태 변경         | `CameraStatus`   |


### 11.3 페이로드 예시

**AlarmEvent**

```json
{
  "alarmId": 1024,
  "cameraId": 1,
  "roiId": 10,
  "severity": "DANGER",
  "type": "WORKER_FORKLIFT_PROXIMITY",
  "message": "작업자-지게차 근접 (거리 92px)",
  "snapshotUrl": "https://.../alarms/1024/snapshot.jpg",
  "occurredAt": "2026-04-26T14:21:33.456Z"
}
```

**DetectionFrame** (고빈도 — 클라이언트는 화면 표시용으로만 사용)

```json
{
  "cameraId": 1,
  "frameTs": "2026-04-26T14:30:00.123Z",
  "objects": [
    { "trackId": 17, "label": "worker",   "bbox": [340,210,420,470], "confidence": 0.91 },
    { "trackId": 33, "label": "forklift", "bbox": [430,250,610,500], "confidence": 0.94 }
  ]
}
```

### 11.4 발행(클라이언트 → 서버)


| 목적지        | 설명                                      |
| ---------- | --------------------------------------- |
| `/app/ack` | 알람 즉시 ACK (페이로드: `{ "alarmId": 1024 }`) |


---

## 12. 데이터 모델 (주요 엔티티)

### Camera


| 필드         | 타입     | 설명                 |
| ---------- | ------ | ------------------ |
| cameraId   | long   | PK                 |
| name       | string | 카메라명               |
| rtspUrl    | string | RTSP 주소            |
| siteId     | long   | 소속 사이트             |
| resolution | string | 해상도                |
| fps        | int    | 프레임레이트             |
| status     | enum   | `ONLINE`/`OFFLINE` |


### Roi


| 필드                      | 타입     | 설명           |
| ----------------------- | ------ | ------------ |
| roiId                   | long   | PK           |
| cameraId                | long   | FK           |
| name                    | string | 구역명          |
| polygon                 | json   | 다각형 꼭짓점 배열   |
| alarmRule               | enum   | 알람 규칙        |
| muteForkliftOnly        | bool   | 지게차 단독 시 뮤팅  |
| dangerDistanceThreshold | int    | 위험 거리 임계(px) |
| active                  | bool   | 활성화          |


### Alarm


| 필드             | 타입       | 설명                     |
| -------------- | -------- | ---------------------- |
| alarmId        | long     | PK                     |
| cameraId       | long     | FK                     |
| roiId          | long     | FK (nullable)          |
| severity       | enum     | `INFO`/`WARN`/`DANGER` |
| type           | enum     | 알람 타입                  |
| status         | enum     | `NEW`/`ACK`/`RESOLVED` |
| message        | string   | 메시지                    |
| detectionsJson | json     | 탐지 객체 배열               |
| clipId         | long     | 연관 클립(nullable)        |
| occurredAt     | datetime | 발생 시각                  |


### Clip


| 필드              | 타입       | 설명          |
| --------------- | -------- | ----------- |
| clipId          | long     | PK          |
| alarmId         | long     | FK          |
| cameraId        | long     | FK          |
| filePath        | string   | 서버 저장 경로    |
| duration        | int      | 길이(초)       |
| startAt / endAt | datetime | 클립 시작/종료 시각 |


### User


| 필드           | 타입     | 설명                          |
| ------------ | ------ | --------------------------- |
| userId       | long   | PK                          |
| username     | string | 로그인 ID                      |
| passwordHash | string | BCrypt 해시                   |
| role         | enum   | `ADMIN`/`OPERATOR`/`VIEWER` |


---

## 13. Enum 정의

```text
Severity      : INFO | WARN | DANGER
AlarmType     : WORKER_INTRUSION | WORKER_FORKLIFT_PROXIMITY | UNKNOWN_OBJECT
AlarmStatus   : NEW | ACK | RESOLVED
AlarmRule     : WORKER_ONLY | WORKER_ALONE_OR_INTERACTION | ANY_OBJECT
ObjectLabel   : worker | forklift
CameraStatus  : ONLINE | OFFLINE
Role          : ADMIN | OPERATOR | VIEWER
```

---

## 14. 변경 이력


| 버전   | 일자         | 변경 내용 | 작성자        |
| ---- | ---------- | ----- | ---------- |
| v1.0 | 2026-04-26 | 최초 작성 | ZONESAFE 팀 |


## 15. 영상 업로드·분석 API (`/videos`)

> 사용자가 MP4 파일을 직접 업로드하여, 마치 실시간 카메라처럼 분석할 수 있다. 과거 사고 영상의 사후 검증, 모델 정확도 평가, 데모·교육용으로 활용.

### 15.1 영상 업로드
- **POST** `/videos/upload` (ADMIN, OPERATOR)
- **Content-Type:** `multipart/form-data`

**Request (multipart)**
| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `file` | File (mp4) | O | MP4 파일 (최대 500MB) |
| `name` | string | X | 표시 이름 (없으면 원본 파일명) |
| `siteId` | long | X | 영상 출처 사이트 (ROI 상속용) |
| `cameraContext` | long | X | 영상 촬영 카메라 ID (ROI/해상도 상속용) |
| `description` | string | X | 영상 설명 (현장 사고 영상, 테스트용 등) |

**Response 201**
```json
{
  "success": true,
  "data": {
    "videoId": 101,
    "filename": "factory_a_2026-04-15.mp4",
    "displayName": "A동 4월 15일 사고 영상",
    "fileSize": 152437824,
    "duration": 600,
    "resolution": "1920x1080",
    "fps": 30,
    "status": "UPLOADED",
    "siteId": 1,
    "cameraContext": 1,
    "description": "지게차 진입 시 작업자 근접 사례",
    "uploadedBy": "admin",
    "uploadedAt": "2026-04-26T14:00:00Z"
  }
}
```

**처리 흐름**
1. 파일 검증 (확장자 mp4, 코덱 H.264/H.265, 최대 크기)
2. 서버 임시 디렉토리에 저장
3. FFprobe로 메타데이터 추출 (duration, resolution, fps, codec)
4. DB에 메타데이터 INSERT, 상태 `UPLOADED`
5. 별도 워커가 백그라운드에서 썸네일 생성

### 15.2 영상 목록 조회
- **GET** `/videos`

**Query Parameters**
| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `status` | enum | `UPLOADED` / `PROCESSING` / `ANALYZED` / `FAILED` |
| `siteId` | long | 사이트 필터 |
| `uploadedBy` | string | 업로더 필터 |
| `from`, `to` | datetime | 업로드 기간 필터 |
| `page`, `size`, `sort` | - | 공통 페이징 |

### 15.3 영상 상세 조회
- **GET** `/videos/{videoId}`

```json
{
  "data": {
    "videoId": 101,
    "filename": "factory_a_2026-04-15.mp4",
    "displayName": "A동 4월 15일 사고 영상",
    "fileSize": 152437824,
    "duration": 600,
    "resolution": "1920x1080",
    "fps": 30,
    "status": "ANALYZED",
    "siteId": 1,
    "cameraContext": 1,
    "uploadedBy": "admin",
    "uploadedAt": "2026-04-26T14:00:00Z",
    "lastAnalyzedAt": "2026-04-26T14:25:00Z",
    "analysisResults": {
      "totalEvents": 5,
      "dangerCount": 2,
      "warnCount": 3,
      "infoCount": 0,
      "generatedClipIds": [5101, 5102, 5103, 5104, 5105]
    },
    "downloadUrl": "/api/v1/videos/101/download",
    "streamUrl": "/api/v1/videos/101/stream",
    "thumbnailUrl": "/api/v1/videos/101/thumbnail"
  }
}
```

### 15.4 영상 삭제
- **DELETE** `/videos/{videoId}` (ADMIN, 소유자)

영상 파일과 분석 결과로 생성된 클립을 함께 삭제한다.

### 15.5 분석 작업 시작
- **POST** `/videos/{videoId}/analyze` (ADMIN, OPERATOR)

업로드된 영상을 현재 활성 모델로 프레임 단위 분석한다.

**Request**
```json
{
  "modelId": 1,
  "roiIds": [10, 11],
  "saveClips": true,
  "skipFrames": 0,
  "alarmRuleOverride": {
    "dangerDistanceThreshold": 150,
    "muteForkliftOnly": true
  }
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `modelId` | long | X | 사용할 모델 ID (기본: 현재 활성 모델) |
| `roiIds` | long[] | X | 적용할 ROI 목록 (기본: cameraContext의 ROI) |
| `saveClips` | bool | X | 위험 이벤트 시 클립 자동 저장 (기본 true) |
| `skipFrames` | int | X | N프레임마다 1번 추론 (성능 가속, 기본 0=전체) |
| `alarmRuleOverride` | object | X | ROI의 기본 규칙을 임시 덮어씀 |

**Response 202 (Accepted)**
```json
{
  "data": {
    "jobId": "anlz-7f3a-b2c1",
    "videoId": 101,
    "status": "QUEUED"
  }
}
```

### 15.6 분석 작업 상태 조회
- **GET** `/videos/{videoId}/analyze/jobs/{jobId}`

```json
{
  "data": {
    "jobId": "anlz-7f3a-b2c1",
    "videoId": 101,
    "status": "RUNNING",
    "progress": 0.42,
    "framesProcessed": 7560,
    "totalFrames": 18000,
    "eventsDetected": 3,
    "startedAt": "2026-04-26T14:20:00Z",
    "estimatedRemainingSec": 145
  }
}
```

**완료 시 (`status: COMPLETED`)**
```json
{
  "data": {
    "jobId": "anlz-7f3a-b2c1",
    "status": "COMPLETED",
    "completedAt": "2026-04-26T14:25:30Z",
    "result": {
      "totalEvents": 5,
      "events": [
        {
          "severity": "DANGER",
          "type": "WORKER_FORKLIFT_PROXIMITY",
          "frameTimestamp": 124.5,
          "occurredAt": "2026-04-15T10:22:14Z",
          "roiId": 10,
          "clipId": 5101
        }
      ],
      "metrics": {
        "avgInferenceMs": 38.2,
        "modelVersion": "yolov8m_zonesafe_v3",
        "totalDetections": 1247
      }
    }
  }
}
```

### 15.7 영상 내 탐지 이벤트 목록
- **GET** `/videos/{videoId}/events`

분석된 영상에서 검출된 이벤트(알람 후보) 시간선을 반환한다. 클립 플레이어에서 타임라인 마커로 표시할 때 사용.

```json
{
  "data": [
    {
      "eventId": 9001,
      "videoId": 101,
      "frameTimestamp": 124.5,
      "severity": "DANGER",
      "type": "WORKER_FORKLIFT_PROXIMITY",
      "clipId": 5101,
      "detections": [...]
    }
  ]
}
```

### 15.8 영상 스트리밍 / 다운로드 / 썸네일
- **GET** `/videos/{videoId}/stream` → `video/mp4` (Range Request)
- **GET** `/videos/{videoId}/download` → `application/octet-stream`
- **GET** `/videos/{videoId}/thumbnail` → `image/jpeg`

### 15.9 활용 시나리오

**시나리오 1 — 사후 검증**: 현장에서 발생한 과거 사고 영상을 업로드하고 현재 모델로 분석. "이 사고를 우리 시스템이 잡아낼 수 있었는가?" 검증.

**시나리오 2 — 모델 정확도 평가 (Validation)**: 라벨링된 검증용 영상을 업로드하고 자동 분석. 검출된 이벤트와 정답을 비교하여 FN/FP 계산. 프로젝트 계획서의 "실제 산업 현장 데이터 기반 Validation" 직접 구현.

**시나리오 3 — 데모·교육**: 시연 영상을 업로드하고 분석 결과를 시각화. 발표·교육 자료로 활용.

**시나리오 4 — 학습 데이터 수집**: 분석 결과의 클립을 오토라벨링 파이프라인으로 전달하여 모델 재학습.

---
