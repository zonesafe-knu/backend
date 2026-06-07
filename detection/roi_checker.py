from shapely.geometry import Point, Polygon


class RoiChecker:
    """
    ROI(Region Of Interest, 관심영역) 폴리곤을 기준으로 위험 여부를 판정하는 클래스.

    [경고 및 제어 시나리오]
      - 작업자(person) 단독 진입            → 경고 (WARN)
      - 작업차(forklift) 단독 진입          → 허용 (알람 없음)
      - 작업차 + 작업자 동시 존재           → 위험 (DANGER)

    [위험 판단의 전체 흐름]
      1) 백엔드/JSON에서 받은 ROI 설정을 폴리곤(Polygon) 객체로 변환해 보관한다.
      2) YOLO가 한 프레임에서 검출한 객체(person / forklift) 목록을 입력받는다.
      3) 각 ROI별로 "내부 진입" 또는 "근접 거리 이내" 여부를 검사한다.
      4) 위 시나리오에 따라 알람 타입/심각도(severity)를 결정한다.
    """

    def __init__(self, roi_configs: list[dict]):
        # roi_configs 예시 항목:
        # {
        #   "roiId": 1, "name": "지게차 통로", "active": true,
        #   "polygon": [[x1,y1], [x2,y2], ...],   # ROI 모양 (다각형 꼭짓점)
        #   "dangerDistanceThreshold": 30          # ROI 외곽선 기준 '근접'으로 볼 픽셀 거리
        # }
        self.rois = []
        for roi in roi_configs:
            # 비활성 ROI는 위험 판정에서 제외 (UI에서 토글로 끈 영역)
            if not roi.get("active", False):
                continue
            coords = roi.get("polygon")
            # 폴리곤은 최소 3개 꼭짓점이 있어야 면적이 생기므로 미만이면 스킵
            if not coords or len(coords) < 3:
                continue
            # shapely Polygon으로 변환 — 이후 contains/distance 계산에 사용
            polygon = Polygon([(p[0], p[1]) for p in coords])
            self.rois.append({
                "roiId": roi["roiId"],
                "name": roi.get("name", ""),
                "polygon": polygon,
                # 근접 거리 임계값 — None이면 '내부 진입'만 위험으로 본다.
                "dangerDistanceThreshold": roi.get("dangerDistanceThreshold"),
            })

    def check_danger(self, detections: list[dict]) -> list[dict]:
        """
        한 프레임의 탐지 결과를 받아 ROI별 위험 알람 리스트를 반환한다.

        detections: [{trackId, label, bbox: [x1,y1,x2,y2], confidence}, ...]
        returns:    [{roiId, roiName, type, severity, trackId, label, bbox}, ...]

        - type:     WORKER_INTRUSION(작업자 단독 침입) / WORKER_FORKLIFT_PROXIMITY(작업자-지게차 동시)
        - severity: DANGER(위험) / WARN(경고)
        """
        alarms = []

        # 1) YOLO 결과를 사람/지게차로 분류 (YOLO는 person, forklift 두 라벨만 사용)
        persons = [d for d in detections if d["label"] == "person"]
        forklifts = [d for d in detections if d["label"] == "forklift"]

        # 1-1) 운전자(탑승자) 제외 — DRIVER_DETECTION_DESIGN.md §7
        # 지게차 운전석에 앉아 있는 사람은 위험 알람 대상이 아님.
        # 여러 forklift가 있을 때 하나라도 탑승으로 판정되면 운전자로 간주(§6-4, OR 결합).
        persons = [
            p for p in persons
            if not any(self._is_riding(p["bbox"], f["bbox"]) for f in forklifts)
        ]

        # 2) ROI별로 위험 판단
        for roi in self.rois:
            polygon = roi["polygon"]
            threshold = roi.get("dangerDistanceThreshold")

            # 사람은 '발 위치'(bbox 하단 중앙) 기준 — 바닥에 서 있는 좌표가 더 정확
            persons_in = self._filter_inside_or_near(persons, polygon, threshold, use_foot=True)
            # 지게차는 '중심점' 기준 — 차체 중심이 ROI에 들어왔는지 판정
            forklifts_in = self._filter_inside_or_near(forklifts, polygon, threshold, use_foot=False)

            # 3) 시나리오 적용
            # (a) 작업차 단독 진입 → 허용: 사람이 없으면 알람 자체를 만들지 않음
            if not persons_in:
                continue

            # (b) 작업자 + 작업차 동시 → 위험 (DANGER)
            if forklifts_in:
                for p in persons_in:
                    alarms.append(self._build_alarm(roi, "WORKER_FORKLIFT_PROXIMITY", "DANGER", p))
            # (c) 작업자 단독 → 경고 (WARN)
            else:
                for p in persons_in:
                    alarms.append(self._build_alarm(roi, "WORKER_INTRUSION", "WARN", p))

        return alarms

    def _filter_inside_or_near(
        self,
        objects: list[dict],
        polygon: Polygon,
        threshold: int | None,
        use_foot: bool,
    ) -> list[dict]:
        """
        주어진 객체들 중 ROI 폴리곤 '내부' 또는 '근접 거리 이내'에 있는 것만 골라낸다.

        - use_foot=True  : bbox 하단 중앙(발 위치)을 기준점으로 사용 (사람용)
        - use_foot=False : bbox 정중앙을 기준점으로 사용 (지게차용)
        - threshold      : ROI 외곽선과의 픽셀 거리 임계값. None이면 '근접' 판정 생략.
        """
        matched = []
        for obj in objects:
            # 객체의 대표 좌표(point) 결정
            pt = self._foot_point(obj["bbox"]) if use_foot else self._center_point(obj["bbox"])

            # (A) ROI 내부에 있으면 즉시 위험 후보
            if polygon.contains(pt):
                matched.append(obj)
            # (B) 외부지만 외곽선까지의 거리가 threshold 이하면 '근접' 위험 후보
            elif threshold and polygon.exterior.distance(pt) <= threshold:
                matched.append(obj)
        return matched

    @staticmethod
    def _foot_point(bbox: list[int]) -> Point:
        # bbox 하단 중앙 — 사람이 바닥에 서 있다고 가정한 위치
        x1, y1, x2, y2 = bbox
        return Point((x1 + x2) / 2, y2)

    @staticmethod
    def _center_point(bbox: list[int]) -> Point:
        # bbox 정중앙 — 차량/물체의 중심 좌표
        x1, y1, x2, y2 = bbox
        return Point((x1 + x2) / 2, (y1 + y2) / 2)

    @staticmethod
    def _is_riding(
        person_bbox: list[int],
        forklift_bbox: list[int],
        cab_floor_ratio: float = 0.5,
    ) -> bool:
        """
        사람이 지게차에 탑승 중인지 판정 (단일 프레임 휴리스틱).

        핵심 아이디어 (DRIVER_DETECTION_DESIGN.md §4~5):
          - 보행자의 발(bbox 하단 y_max)은 지게차 bbox '바닥 라인'에 위치
          - 운전자의 발은 지게차 bbox '상단부'(운전석 발판)에 위치
          → 사람 bbox의 발이 forklift bbox 상단으로부터 cab_floor_ratio 비율 안에
            있으면 탑승으로 판정.

        cab_floor_ratio: 0.5 → forklift bbox 위쪽 50%를 운전석 영역으로 간주.
                         카메라 앵글에 따라 0.3~0.5 사이에서 튜닝 (§6-2, §8).
        """
        px1, py1, px2, py2 = person_bbox
        fx1, fy1, fx2, fy2 = forklift_bbox

        # 사전 필터(§6-3): 지게차가 사람보다 너무 작으면 운전자 가능성 거의 없음 — 빠르게 False
        person_height = py2 - py1
        forklift_height = fy2 - fy1
        if forklift_height < person_height * 0.6:
            return False

        px = (px1 + px2) / 2  # 발의 x (bbox 가로 중심)
        py = py2              # 발의 y (bbox 하단)

        # 1) 발이 forklift bbox 가로 범위 안에 있어야 함
        if not (fx1 <= px <= fx2):
            return False

        # 2) 발이 forklift bbox 위쪽 영역(운전석)에 있어야 함
        cab_floor_y = fy1 + (fy2 - fy1) * cab_floor_ratio
        return py < cab_floor_y

    @staticmethod
    def _build_alarm(roi: dict, alarm_type: str, severity: str, detection: dict) -> dict:
        # main.py의 알람 디스패처에 넘길 통일된 형식의 알람 객체를 생성
        return {
            "roiId": roi["roiId"],
            "roiName": roi["name"],
            "type": alarm_type,
            "severity": severity,
            "trackId": detection["trackId"],
            "label": detection["label"],
            "bbox": detection["bbox"],
        }