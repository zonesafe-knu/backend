# 🦺 ZONESAFE

### CNN 기반 산업현장 작업자 안전관리 솔루션

> **기존 CCTV 인프라와 AI 객체 탐지 기술을 활용하여 작업자와 지게차를 실시간으로 감지하고, 위험구역(ROI) 진입 상황을 판단하는 산업현장 안전관리 시스템**

ZONESAFE는 산업현장에서 발생할 수 있는 **작업자-지게차 간 충돌 및 협착 사고를 예방하기 위한 AI 기반 안전관리 솔루션**입니다.

기존 산업현장에서 사용되는 라이트 커튼(Light Curtain)은 물체가 센서를 통과했는지만 판단하기 때문에 작업자와 화물·장비를 구분하기 어렵고, 작업 환경이 변경될 때마다 위험구역을 유연하게 변경하기 어렵다는 한계가 있습니다.

ZONESAFE는 이러한 문제를 해결하기 위해 **기존 CCTV 영상과 YOLOv8 기반 객체 탐지 기술**을 활용합니다. 작업자(Person)와 지게차(Forklift)를 탐지하고 관리자가 설정한 위험구역(ROI) 진입 여부를 분석하여 위험 상황 발생 시 실시간 경고를 생성합니다.

---

## 📌 Project Overview

산업현장에서는 지게차와 작업자의 동선이 겹치면서 충돌 및 협착 사고가 발생할 위험이 존재합니다.

기존의 물리적 안전장치는 다음과 같은 한계가 있습니다.

* 작업자와 화물·장비를 구분하기 어려움
* 단순 센서 감지로 인한 오탐지 발생
* 작업 동선 변경 시 위험구역 수정이 어려움
* 별도의 센서 및 장비 설치로 인한 비용 증가
* 반복적인 경보로 인한 관리자의 피로도 증가

ZONESAFE는 별도의 센서를 추가하는 대신 **기존 CCTV 인프라를 활용한 Vision AI 기반 안전관리 시스템**을 구축하여 이러한 문제를 개선하고자 했습니다.

---

## 🎯 Project Goals

ZONESAFE의 주요 목표는 다음과 같습니다.

### 1. 실시간 객체 탐지

YOLOv8 기반 객체 탐지 모델을 이용하여 CCTV 영상에서

* `Person`
* `Forklift`

객체를 실시간으로 탐지합니다.

### 2. 위험구역(ROI) 설정 및 수정

관리자가 웹 대시보드에서 직접 위험구역(ROI)을 설정하고 수정할 수 있도록 구현했습니다.

작업 환경이나 장비 배치가 변경되더라도 별도의 장비 설치 없이 위험구역을 유연하게 관리할 수 있습니다.

### 3. 위험 상황 자동 판단

탐지된 객체와 ROI 정보를 기반으로 위험 상황을 판단합니다.

| ROI 진입 객체 | 판단        |
| --------- | --------- |
| 작업자       | 🚨 DANGER |
| 지게차       | SAFE      |
| 작업자 + 지게차 | 🚨 DANGER |
| 객체 없음     | SAFE      |

### 4. 실시간 모니터링

관리자는 웹 대시보드를 통해

* CCTV 영상
* 객체 탐지 결과
* 위험구역
* 위험 알람
* 알람 히스토리
* 위험 상황 영상 클립

등을 확인할 수 있습니다.

---

# ⚙️ System Flow

<img width="566" height="208" alt="스크린샷 2026-08-17 오후 5 19 17" src="https://github.com/user-attachments/assets/1f143698-90a4-4998-b913-138cc5e56032" />

---

# 🏗 System Architecture

<img width="1056" height="569" alt="스크린샷 2026-08-17 오후 5 19 55" src="https://github.com/user-attachments/assets/41c1a395-f426-4cf5-a355-e0645dbed878" />

---

# ✨ Key Features

## 📹 CCTV 및 영상 관리

* CCTV 카메라 등록 및 관리
* 영상 업로드
* 영상 자동 분석
* H.264 영상 변환
* HTTP Range 기반 영상 스트리밍

## 🤖 AI 객체 탐지

* YOLOv8 기반 객체 탐지
* Person / Forklift 클래스 탐지
* 실시간 Detection 결과 생성
* GPU 기반 AI 추론

## 🚧 ROI 위험구역 관리

* 카메라별 위험구역 생성
* 웹 UI에서 ROI 좌표 직접 설정
* 위험구역 수정 및 관리
* 탐지 객체의 ROI 진입 여부 판단

## 🚨 실시간 위험 알람

위험 상황 발생 시 Backend에서 Alarm을 생성하고 WebSocket(STOMP)을 이용하여 Frontend에 실시간으로 전달합니다.


## 🎞 위험 상황 클립 자동 저장

위험 상황 발생 시 해당 시점을 기준으로 전후 영상을 자동으로 추출합니다.

```text
위험 발생 시점 t

t - 5 sec ──────── t ──────── t + 5 sec
       └──── 위험 영상 Clip ────┘
```

FFmpeg 기반 클립 추출 작업은 비동기로 처리하여 알람 생성 과정이 지연되지 않도록 구현했습니다.

## 📊 위험 히스토리 관리

관리자는 과거 위험 상황을 검색하고 확인할 수 있습니다.

* Camera
* ROI
* Severity
* Type
* Status
* 발생 기간

등의 조건을 조합하여 위험 알람을 조회할 수 있습니다.

---

# 🧠 AI Model

## YOLOv8 Object Detection

AI 모델은 **Ultralytics YOLOv8**을 기반으로 구축했습니다.


### Training Environment

| Environment | Configuration         |
| ----------- | --------------------- |
| OS          | Ubuntu Linux          |
| Python      | 3.8.10                |
| Framework   | PyTorch 2.4.1         |
| CUDA        | 12.1                  |
| YOLO        | Ultralytics YOLOv8    |
| GPU         | NVIDIA RTX A6000 48GB |
| Model       | YOLOv8n               |
| Image Size  | 640 × 640             |
| Batch Size  | 16                    |
| Epoch       | 50                    |

---

# 📚 Dataset & Training

실제 공장의 CCTV 데이터를 중심으로 모델을 학습했습니다.

### 4️⃣ Labeling 개선

초기 학습 모델을 활용한 Auto Labeling을 적용하고 라벨링 기준을 개선하여 작업자와 지게차 탐지 성능을 향상시켰습니다.

---

# 📈 Model Performance

데이터 정제, 외부 데이터 추가 및 데이터 증강을 단계적으로 적용하며 모델의 일반화 성능을 개선했습니다.

| Model       | Training Strategy                 |   mAP@0.5 |
| ----------- | --------------------------------- | --------: |
| Model A     | Baseline                          |    0.7652 |
| Model B     | Baseline + Data Augmentation      |     0.714 |
| Model C     | Frame Cleaning + External Dataset |     0.845 |
| **Model D** | **Model C + Data Augmentation**   | **0.905** |

최종 모델의 주요 성능은 다음과 같습니다.

| Metric      |     Score |
| ----------- | --------: |
| **mAP@0.5** | **0.905** |
| Precision   |     0.885 |
| Recall      |     0.864 |
| F1-Score    |     0.874 |

단순히 학습 데이터의 양을 증가시키는 것보다 **데이터 다양성과 품질을 확보하는 것이 모델의 일반화 성능 향상에 중요하다는 점**을 확인했습니다.

---


# 🛠 Tech Stack

### Frontend

* React
* Vite
* WebSocket / STOMP

### Backend

* Java
* Spring Boot
* Spring Data JPA
* WebSocket / STOMP
* FFmpeg
* Redis

### AI

* Python
* PyTorch
* Ultralytics YOLOv8
* OpenCV
* CUDA

### Database

* MySQL
* Redis

### Infrastructure

* Ubuntu Linux
* NVIDIA RTX A6000
* Git / GitHub

---

# 🏭 Expected Applications

ZONESAFE는 다양한 산업현장으로 확장할 수 있습니다.

| 산업 분야  | 적용 예시              |
| ------ | ------------------ |
| 제조     | 공장 내 지게차-작업자 충돌 감지 |
| 건설     | 중장비 주변 작업자 접근 감지   |
| 물류     | 물류센터 지게차 동선 안전관리   |
| 창고     | 작업자 위험구역 진입 감지     |
| 스마트팩토리 | AI 기반 통합 안전관리 시스템  |

---


# 👥 Team ZONESAFE

| 역할       | 담당                                              |
| -------- | ----------------------------------------------- |
| Backend  | Camera / API / Alarm / 실시간 WebSocket / Pipeline |
| Backend  | ROI / Video Clip / Video Upload / API           |
| AI       | YOLOv8 모델 학습 / 데이터셋 구축 / AI 추론 파이프라인            |
| Frontend | 실시간 Monitoring Dashboard / Detection UI         |
| Frontend | Login / History / Alarm Detail                  |

**Team ZONESAFE**는 Frontend 2명, Backend 2명, AI 1명으로 구성되어 프로젝트를 진행했습니다.

---
