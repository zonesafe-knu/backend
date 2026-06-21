from shapely.geometry import Point, Polygon
from shapely.geometry import box as shapely_box


class RoiChecker:
    """ROI 폴리곤 내부 진입 및 근접 거리 판단"""

    def __init__(self, roi_configs: list[dict]):
        self.rois = []
        for roi in roi_configs:
            if not roi.get("active", False):
                continue
            coords = roi.get("polygon")
            if not coords or len(coords) < 3:
                continue
            polygon = Polygon([(p[0], p[1]) for p in coords])
            self.rois.append({
                "roiId": roi["roiId"],
                "name": roi.get("name", ""),
                "polygon": polygon,
                "alarmRule": roi["alarmRule"],
                "muteForkliftOnly": roi.get("muteForkliftOnly", True),
                "dangerDistanceThreshold": roi.get("dangerDistanceThreshold"),
            })

    def check_danger(self, detections: list[dict]) -> list[dict]:
        """
        탐지 객체 리스트를 받아 ROI 위험 판단 후 알람 이벤트 목록 반환.

        위험 판단 기준:
          - 사람(person/worker)의 bbox가 위험구역 폴리곤과 50% 이상 겹칠 때 DANGER 알람 발생
          - 지게차는 작업구역 내 정상 운행이므로 알람 대상에서 제외

        detections: [{trackId, label, bbox: [x1,y1,x2,y2], confidence}]
        returns:    [{roiId, roiName, type, severity, trackId, label, bbox}]
        """
        alarms = []

        persons = [d for d in detections if d["label"].lower() in ("person", "worker")]

        for roi in self.rois:
            polygon = roi["polygon"]

            persons_in = self._filter_by_overlap(persons, polygon)

            for p in persons_in:
                alarms.append(self._build_alarm(roi, "WORKER_INTRUSION", "DANGER", p))

        return alarms

    OVERLAP_THRESHOLD = 0.3  # bbox 면적 대비 ROI 겹침 비율 기준

    def _filter_by_overlap(
        self,
        objects: list[dict],
        polygon: Polygon,
    ) -> list[dict]:
        matched = []
        for obj in objects:
            x1, y1, x2, y2 = obj["bbox"]
            person_box = shapely_box(x1, y1, x2, y2)
            intersection = polygon.intersection(person_box).area
            overlap_ratio = intersection / person_box.area if person_box.area > 0 else 0
            if overlap_ratio >= self.OVERLAP_THRESHOLD:
                matched.append(obj)
        return matched

    @staticmethod
    def _build_alarm(roi: dict, alarm_type: str, severity: str, detection: dict) -> dict:
        return {
            "roiId": roi["roiId"],
            "roiName": roi["name"],
            "type": alarm_type,
            "severity": severity,
            "trackId": detection["trackId"],
            "label": detection["label"],
            "bbox": detection["bbox"],
        }
