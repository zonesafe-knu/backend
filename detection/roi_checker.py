from shapely.geometry import Point, Polygon


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

        detections: [{trackId, label, bbox: [x1,y1,x2,y2], confidence}]
        returns:    [{roiId, roiName, type, severity, trackId, label, bbox}]
        """
        alarms = []

        persons = [d for d in detections if d["label"] in ("person", "worker")]
        forklifts = [d for d in detections if d["label"] == "forklift"]

        for roi in self.rois:
            polygon = roi["polygon"]
            threshold = roi.get("dangerDistanceThreshold")
            alarm_rule = roi["alarmRule"]

            persons_in = self._filter_inside_or_near(persons, polygon, threshold, use_foot=True)
            forklifts_in = self._filter_inside_or_near(forklifts, polygon, threshold, use_foot=False)

            if alarm_rule == "WORKER_ONLY":
                for p in persons_in:
                    alarms.append(self._build_alarm(roi, "WORKER_INTRUSION", "DANGER", p))

            elif alarm_rule == "WORKER_ALONE_OR_INTERACTION":
                for p in persons_in:
                    if forklifts_in:
                        alarms.append(self._build_alarm(roi, "WORKER_FORKLIFT_PROXIMITY", "DANGER", p))
                    else:
                        alarms.append(self._build_alarm(roi, "WORKER_INTRUSION", "WARN", p))

            elif alarm_rule == "ANY_OBJECT":
                for obj in persons_in + forklifts_in:
                    alarms.append(self._build_alarm(roi, "WORKER_INTRUSION", "WARN", obj))

        return alarms

    def _filter_inside_or_near(
        self,
        objects: list[dict],
        polygon: Polygon,
        threshold: int | None,
        use_foot: bool,
    ) -> list[dict]:
        matched = []
        for obj in objects:
            pt = self._foot_point(obj["bbox"]) if use_foot else self._center_point(obj["bbox"])
            if polygon.contains(pt):
                matched.append(obj)
            elif threshold and polygon.exterior.distance(pt) <= threshold:
                matched.append(obj)
        return matched

    @staticmethod
    def _foot_point(bbox: list[int]) -> Point:
        x1, y1, x2, y2 = bbox
        return Point((x1 + x2) / 2, y2)

    @staticmethod
    def _center_point(bbox: list[int]) -> Point:
        x1, y1, x2, y2 = bbox
        return Point((x1 + x2) / 2, (y1 + y2) / 2)

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
