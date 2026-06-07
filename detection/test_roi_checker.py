"""ROI 판단 로직 단위 테스트 — 백엔드/영상 없이 실행 가능"""

from roi_checker import RoiChecker


def test_person_inside_roi():
    """사람 단독이 ROI 내부에 있을 때 WARN 알람이 발생하는지"""
    rois = [{
        "roiId": 1,
        "name": "위험구역A",
        "polygon": [[100, 100], [400, 100], [400, 400], [100, 400]],
        "alarmRule": "WORKER_ONLY",
        "muteForkliftOnly": True,
        "dangerDistanceThreshold": 50,
        "active": True,
    }]
    checker = RoiChecker(rois)

    # bbox [200,150,280,380] → 발 위치 (240, 380) → ROI 내부
    detections = [{"trackId": 1, "label": "person", "bbox": [200, 150, 280, 380], "confidence": 0.9}]
    alarms = checker.check_danger(detections)

    assert len(alarms) == 1, f"알람 1개 기대했는데 {len(alarms)}개 발생"
    assert alarms[0]["type"] == "WORKER_INTRUSION"
    assert alarms[0]["severity"] == "WARN"
    print("PASS: 사람 단독이 ROI 내부 → WARN 알람 정상 발생")


def test_person_outside_roi():
    """사람이 ROI 바깥에 있을 때 알람이 없는지"""
    rois = [{
        "roiId": 1,
        "name": "위험구역A",
        "polygon": [[100, 100], [400, 100], [400, 400], [100, 400]],
        "alarmRule": "WORKER_ONLY",
        "muteForkliftOnly": True,
        "dangerDistanceThreshold": None,
        "active": True,
    }]
    checker = RoiChecker(rois)

    # bbox [500,500,580,700] → 발 위치 (540, 700) → ROI 바깥
    detections = [{"trackId": 1, "label": "person", "bbox": [500, 500, 580, 700], "confidence": 0.85}]
    alarms = checker.check_danger(detections)

    assert len(alarms) == 0, f"알람 0개 기대했는데 {len(alarms)}개 발생"
    print("PASS: 사람이 ROI 바깥 → 알람 없음")


def test_person_near_roi():
    """사람이 ROI 근처(dangerDistanceThreshold 이내)일 때 알람 발생하는지"""
    rois = [{
        "roiId": 1,
        "name": "위험구역A",
        "polygon": [[100, 100], [400, 100], [400, 400], [100, 400]],
        "alarmRule": "WORKER_ONLY",
        "muteForkliftOnly": True,
        "dangerDistanceThreshold": 50,
        "active": True,
    }]
    checker = RoiChecker(rois)

    # bbox [410,150,490,380] → 발 위치 (450, 380) → ROI 바깥이지만 경계에서 50px 이내
    detections = [{"trackId": 1, "label": "person", "bbox": [410, 150, 490, 380], "confidence": 0.88}]
    alarms = checker.check_danger(detections)

    assert len(alarms) == 1, f"알람 1개 기대했는데 {len(alarms)}개 발생"
    print("PASS: 사람이 ROI 근접(50px 이내) → WARN 알람 정상 발생")


def test_worker_forklift_interaction():
    """작업자+지게차 동시 감지 시 WORKER_FORKLIFT_PROXIMITY 알람"""
    rois = [{
        "roiId": 2,
        "name": "작업구역B",
        "polygon": [[0, 0], [600, 0], [600, 500], [0, 500]],
        "alarmRule": "WORKER_ALONE_OR_INTERACTION",
        "muteForkliftOnly": False,
        "dangerDistanceThreshold": 30,
        "active": True,
    }]
    checker = RoiChecker(rois)

    detections = [
        {"trackId": 1, "label": "person", "bbox": [100, 100, 160, 300], "confidence": 0.92},
        {"trackId": 2, "label": "forklift", "bbox": [200, 150, 400, 350], "confidence": 0.95},
    ]
    alarms = checker.check_danger(detections)

    assert len(alarms) == 1
    assert alarms[0]["type"] == "WORKER_FORKLIFT_PROXIMITY"
    assert alarms[0]["severity"] == "DANGER"
    print("PASS: 작업자+지게차 → WORKER_FORKLIFT_PROXIMITY 알람 정상 발생")


def test_inactive_roi_ignored():
    """비활성 ROI는 무시되는지"""
    rois = [{
        "roiId": 1,
        "name": "비활성구역",
        "polygon": [[0, 0], [1000, 0], [1000, 1000], [0, 1000]],
        "alarmRule": "ANY_OBJECT",
        "active": False,
    }]
    checker = RoiChecker(rois)

    detections = [{"trackId": 1, "label": "person", "bbox": [100, 100, 200, 300], "confidence": 0.9}]
    alarms = checker.check_danger(detections)

    assert len(alarms) == 0
    print("PASS: 비활성 ROI → 무시됨")


def test_driver_excluded():
    """지게차 운전석에 앉은 사람(운전자)은 위험 알람 대상에서 제외되는지"""
    rois = [{
        "roiId": 1,
        "name": "위험구역A",
        "polygon": [[0, 0], [1000, 0], [1000, 1000], [0, 1000]],
        "active": True,
    }]
    checker = RoiChecker(rois)

    # forklift bbox [200,300,600,700] → cab_floor_y = 300 + 400*0.5 = 500
    # person bbox [350,200,450,450] → 발 y=450 (cab_floor_y=500보다 위쪽 → 운전석)
    # → person 운전자로 판정되어 제거 → forklift 단독 진입은 시나리오 ④에서 허용
    detections = [
        {"trackId": 1, "label": "person", "bbox": [350, 200, 450, 450], "confidence": 0.9},
        {"trackId": 2, "label": "forklift", "bbox": [200, 300, 600, 700], "confidence": 0.95},
    ]
    alarms = checker.check_danger(detections)

    assert len(alarms) == 0, f"운전자는 알람 제외 기대했는데 {len(alarms)}개 발생"
    print("PASS: 지게차 운전자 → 알람 제외 (forklift 단독 허용)")


def test_pedestrian_in_front_of_forklift():
    """지게차 앞을 걷는 보행자(발이 바닥)는 운전자로 오인되지 않고 위험 판정되는지"""
    rois = [{
        "roiId": 1,
        "name": "위험구역A",
        "polygon": [[0, 0], [1000, 0], [1000, 1000], [0, 1000]],
        "active": True,
    }]
    checker = RoiChecker(rois)

    # forklift bbox [200,300,600,700] → cab_floor_y = 500
    # person bbox [350,400,450,750] → 발 y=750 (cab_floor_y=500보다 아래 → 바닥)
    # → 운전자 아님 → 작업자+지게차 동시 진입 → DANGER
    detections = [
        {"trackId": 1, "label": "person", "bbox": [350, 400, 450, 750], "confidence": 0.9},
        {"trackId": 2, "label": "forklift", "bbox": [200, 300, 600, 700], "confidence": 0.95},
    ]
    alarms = checker.check_danger(detections)

    assert len(alarms) == 1, f"알람 1개 기대했는데 {len(alarms)}개 발생"
    assert alarms[0]["type"] == "WORKER_FORKLIFT_PROXIMITY"
    assert alarms[0]["severity"] == "DANGER"
    print("PASS: 지게차 앞 보행자 → DANGER 알람 정상 발생 (운전자 오인 없음)")


def test_is_riding_size_filter():
    """지게차가 사람보다 작으면 운전자 판정 자체가 사전 필터에서 차단되는지 (§6-3)"""
    # person height=500, forklift height=250 → 250 < 500*0.6=300 → 사전 필터 차단
    assert RoiChecker._is_riding(
        person_bbox=[100, 100, 200, 600],
        forklift_bbox=[150, 350, 250, 600],
    ) is False
    print("PASS: 지게차가 사람보다 작음 → 운전자 아님 (사전 필터 차단)")


if __name__ == "__main__":
    test_person_inside_roi()
    test_person_outside_roi()
    test_person_near_roi()
    test_worker_forklift_interaction()
    test_inactive_roi_ignored()
    test_driver_excluded()
    test_pedestrian_in_front_of_forklift()
    test_is_riding_size_filter()
    print("\n모든 테스트 통과!")
