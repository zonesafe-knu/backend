"""ROI 판단 로직 단위 테스트 — 백엔드/영상 없이 실행 가능"""

from roi_checker import RoiChecker


def test_person_inside_roi():
    """사람이 ROI 내부에 있을 때 알람이 발생하는지"""
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
    assert alarms[0]["severity"] == "DANGER"
    print("PASS: 사람이 ROI 내부 → DANGER 알람 정상 발생")


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
    print("PASS: 사람이 ROI 근접(50px 이내) → DANGER 알람 정상 발생")


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


if __name__ == "__main__":
    test_person_inside_roi()
    test_person_outside_roi()
    test_person_near_roi()
    test_worker_forklift_interaction()
    test_inactive_roi_ignored()
    print("\n모든 테스트 통과!")
