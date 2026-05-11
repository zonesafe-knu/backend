package me.zonesafe.zonesafe_be.service;

import lombok.RequiredArgsConstructor;
import me.zonesafe.zonesafe_be.dto.DetectionSnapshotResponseDto;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DetectionService {
    // TODO: 추후 AI 엔진이 쏴주는 Redis 데이터를 조회하는 로직으로 대체해야 합니다!
    // 현재는 API 스펙 연동 테스트를 위해 명세서와 동일한 더미 데이터를 반환합니다.

    public DetectionSnapshotResponseDto getDetectionSnapshot(Long cameraId) {
        return DetectionSnapshotResponseDto.builder()
                .cameraId(cameraId)
                .frameTimestamp(ZonedDateTime.now())
                .fps(28.4)
                .modelVersion("yolov8m_zonesafe_v3")
                .objects(List.of(
                        DetectionSnapshotResponseDto.DetectedObjectDto.builder()
                                .trackId(17L)
                                .label("worker")
                                .bbox(List.of(340, 210, 420, 470))
                                .confidence(0.91)
                                .inRoi(List.of(10L))
                                .build(),
                        DetectionSnapshotResponseDto.DetectedObjectDto.builder()
                                .trackId(33L)
                                .label("forklift")
                                .bbox(List.of(430, 250, 610, 500))
                                .confidence(0.94)
                                .inRoi(List.of(10L))
                                .build()
                ))
                .build();
    }
}
