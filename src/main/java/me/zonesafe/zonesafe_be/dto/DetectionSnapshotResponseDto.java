package me.zonesafe.zonesafe_be.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.ZonedDateTime;
import java.util.List;

@Getter
@Builder
public class DetectionSnapshotResponseDto {
    private Long cameraId;
    private ZonedDateTime frameTimestamp;
    private List<DetectedObjectDto> objects;
    private Double fps;
    private String modelVersion;

    @Getter
    @Builder
    public static class DetectedObjectDto {
        private Long trackId;
        private String label;
        private List<Integer> bbox;
        private Double confidence;
        private List<Long> inRoi;
    }
}
