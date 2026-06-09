import requests
from datetime import datetime, timezone


class BackendClient:
    def __init__(self, base_url: str):
        self.base_url = base_url.rstrip("/")

    def get_rois(self, camera_id: int | None = None) -> list[dict]:
        params = {}
        if camera_id is not None:
            params["cameraId"] = camera_id
        resp = requests.get(
            f"{self.base_url}/api/v1/rois",
            params=params,
        )
        resp.raise_for_status()
        data = resp.json()
        if isinstance(data, dict) and "data" in data:
            return data["data"]
        return data

    def send_detection_frame(self, camera_id: int, frame_ts: str, objects: list[dict], video_time_sec: float | None = None, video_id: int | None = None):
        payload = {
            "cameraId": camera_id,
            "frameTs": frame_ts,
            "objects": objects,
        }
        if video_time_sec is not None:
            payload["videoTimeSec"] = round(video_time_sec, 3)
        if video_id is not None:
            payload["videoId"] = video_id
        resp = requests.post(
            f"{self.base_url}/api/v1/internal/detections/frame",
            json=payload,
        )
        resp.raise_for_status()

    def create_alarm(
        self,
        camera_id: int,
        roi_id: int,
        severity: str,
        alarm_type: str,
        message: str,
        detections_json: str | None = None,
        snapshot_url: str | None = None,
        video_id: int | None = None,
        video_time_sec: float | None = None,
    ) -> dict:
        payload = {
            "cameraId": camera_id,
            "roiId": roi_id,
            "severity": severity,
            "type": alarm_type,
            "message": message,
            "occurredAt": datetime.now(timezone.utc).isoformat(),
        }
        if detections_json:
            payload["detectionsJson"] = detections_json
        if snapshot_url:
            payload["snapshotUrl"] = snapshot_url
        if video_id is not None:
            payload["videoId"] = video_id
        if video_time_sec is not None:
            payload["videoTimeSec"] = round(video_time_sec, 3)

        resp = requests.post(
            f"{self.base_url}/api/v1/internal/alarms",
            json=payload,
        )
        resp.raise_for_status()
        return resp.json()

    def create_video_event(
        self,
        video_id: int,
        job_id: str | None,
        frame_timestamp: float,
        severity: str,
        alarm_type: str,
        roi_id: int | None = None,
        detections_json: str | None = None,
    ):
        payload = {
            "videoId": video_id,
            "jobId": job_id,
            "frameTimestamp": frame_timestamp,
            "severity": severity,
            "type": alarm_type,
            "roiId": roi_id,
            "detectionsJson": detections_json,
            "occurredAt": datetime.now(timezone.utc).isoformat(),
        }
        resp = requests.post(
            f"{self.base_url}/api/v1/internal/videos/{video_id}/events",
            json=payload,
        )
        resp.raise_for_status()
