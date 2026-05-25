"""
ZoneSafe YOLO Detection — 카메라 ROI 기반 위험구역 감지 스크립트

사용법 (로컬 테스트 — 백엔드 없이):
    python main.py --source ./test.mp4 --camera-id 1 --roi-json ./sample_rois.json

사용법 (백엔드 연동):
    python main.py --source ./test.mp4 --camera-id 1 --backend-url http://localhost:8080
    python main.py --source rtsp://카메라주소 --camera-id 1 --backend-url http://localhost:8080
"""

import argparse
import json
import time
from datetime import datetime, timezone

import cv2
from ultralytics import YOLO

from api_client import BackendClient
from roi_checker import RoiChecker


def parse_args():
    parser = argparse.ArgumentParser(description="ZoneSafe YOLO 위험구역 감지")
    parser.add_argument("--source", required=True, help="영상 소스 (mp4 파일 경로 또는 RTSP URL)")
    parser.add_argument("--camera-id", type=int, required=True, help="백엔드에 등록된 카메라 ID")
    parser.add_argument("--model", default="yolov8n.pt", help="YOLO 모델 파일 경로")
    parser.add_argument("--confidence", type=float, default=0.5, help="탐지 confidence 임계값")

    roi_group = parser.add_mutually_exclusive_group(required=True)
    roi_group.add_argument("--roi-json", help="ROI 설정 JSON 파일 경로 (백엔드 없이 테스트용)")
    roi_group.add_argument("--backend-url", help="백엔드 API URL (ROI를 백엔드에서 가져옴)")

    parser.add_argument("--skip-frames", type=int, default=5, help="N 프레임마다 1번 분석 (기본: 5)")
    parser.add_argument("--roi-refresh", type=int, default=30, help="ROI 설정 갱신 주기 — 초 (백엔드 연동 시)")
    parser.add_argument("--alarm-cooldown", type=int, default=10, help="같은 ROI 알람 재전송 대기 시간 (초)")
    return parser.parse_args()


def load_rois_from_file(path: str) -> list[dict]:
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def build_detections(results, model) -> list[dict]:
    detections = []
    for result in results:
        if result.boxes is None:
            continue
        for i, box in enumerate(result.boxes):
            cls_id = int(box.cls[0])
            label = model.names[cls_id]
            bbox = [int(v) for v in box.xyxy[0].tolist()]
            track_id = int(box.id[0]) if box.id is not None else i
            detections.append({
                "trackId": track_id,
                "label": label,
                "bbox": bbox,
                "confidence": round(float(box.conf[0]), 3),
            })
    return detections


def main():
    args = parse_args()

    print(f"모델 로딩: {args.model}")
    model = YOLO(args.model)

    client = BackendClient(args.backend_url) if args.backend_url else None

    # ROI 초기 로드
    if args.roi_json:
        rois = load_rois_from_file(args.roi_json)
    else:
        rois = client.get_rois(args.camera_id)

    roi_checker = RoiChecker(rois)
    print(f"활성 ROI {len(roi_checker.rois)}개 로드 완료")

    if len(roi_checker.rois) == 0:
        print("경고: 활성 ROI가 없습니다. ROI 설정을 확인해주세요.")
        return

    cap = cv2.VideoCapture(args.source)
    if not cap.isOpened():
        print(f"영상 소스를 열 수 없습니다: {args.source}")
        return

    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    is_file = total_frames > 0
    if is_file:
        print(f"영상 파일: {total_frames}프레임, {fps:.1f}fps, {total_frames / fps:.1f}초")
    else:
        print(f"실시간 스트림 연결됨 ({fps:.1f}fps)")

    print(f"카메라 {args.camera_id} 탐지 시작 (매 {args.skip_frames}프레임 분석)")

    alarm_cooldowns: dict[int, float] = {}
    all_events: list[dict] = []
    frame_idx = 0
    last_roi_refresh = time.time()

    try:
        while cap.isOpened():
            ret, frame = cap.read()
            if not ret:
                break

            frame_idx += 1
            if frame_idx % args.skip_frames != 0:
                continue

            now = time.time()

            # 백엔드 연동 시 ROI 주기적 갱신 (실시간 스트림용)
            if client and not is_file and (now - last_roi_refresh > args.roi_refresh):
                try:
                    rois = client.get_rois(args.camera_id)
                    roi_checker = RoiChecker(rois)
                    last_roi_refresh = now
                    print(f"ROI 갱신 완료 — 활성 ROI {len(roi_checker.rois)}개")
                except Exception as e:
                    print(f"ROI 갱신 실패: {e}")

            results = model.track(frame, conf=args.confidence, persist=True, verbose=False)
            detections = build_detections(results, model)

            if not detections:
                continue

            # 백엔드에 탐지 프레임 전송
            frame_ts = datetime.now(timezone.utc).isoformat()
            if client:
                try:
                    client.send_detection_frame(args.camera_id, frame_ts, detections)
                except Exception as e:
                    print(f"탐지 프레임 전송 실패: {e}")

            # ROI 위험 판단
            alarms = roi_checker.check_danger(detections)
            for alarm in alarms:
                roi_id = alarm["roiId"]
                if now - alarm_cooldowns.get(roi_id, 0) < args.alarm_cooldown:
                    continue

                alarm_cooldowns[roi_id] = now
                frame_time_sec = round(frame_idx / fps, 2) if is_file else None
                message = f"{alarm['roiName']}에서 {alarm['label']} 감지"

                event = {
                    "frameTimestamp": frame_time_sec,
                    "severity": alarm["severity"],
                    "type": alarm["type"],
                    "roiId": alarm["roiId"],
                    "roiName": alarm["roiName"],
                    "label": alarm["label"],
                    "detections": detections,
                }
                all_events.append(event)
                print(f"  [{alarm['severity']}] {message}")

                # 백엔드에 알람 전송
                if client:
                    try:
                        client.create_alarm(
                            camera_id=args.camera_id,
                            roi_id=roi_id,
                            severity=alarm["severity"],
                            alarm_type=alarm["type"],
                            message=message,
                            detections_json=json.dumps(detections),
                        )
                    except Exception as e:
                        print(f"  알람 전송 실패: {e}")

            # 진행률 (파일 분석 시)
            if is_file and frame_idx % (args.skip_frames * 100) == 0:
                progress = frame_idx / total_frames * 100
                print(f"  진행률: {progress:.0f}% ({frame_idx}/{total_frames})")

    finally:
        cap.release()

    print(f"\n분석 완료 — {len(all_events)}건 위험 이벤트 감지")

    # 파일 분석인 경우 결과를 JSON으로 저장
    if is_file and all_events:
        result_path = args.source.rsplit(".", 1)[0] + "_events.json"
        with open(result_path, "w", encoding="utf-8") as f:
            json.dump(all_events, f, ensure_ascii=False, indent=2)
        print(f"결과 저장: {result_path}")


if __name__ == "__main__":
    main()
