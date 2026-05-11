package me.zonesafe.zonesafe_be.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetectionSnapshotResponseDto {
    private Long cameraId;
    private ZonedDateTime frameTimestamp;
    private List<DetectedObjectDto> objects;
    private Double fps;
    private String modelVersion;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetectedObjectDto {
        private Long trackId;
        private String label;
        private List<Integer> bbox;
        private Double confidence;
        private List<Long> inRoi;
    }
}
