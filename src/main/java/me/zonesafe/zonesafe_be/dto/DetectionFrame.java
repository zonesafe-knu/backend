package me.zonesafe.zonesafe_be.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetectionFrame {
    private Long cameraId;
    private Long videoId;
    private ZonedDateTime frameTs;
    private Double videoTimeSec;
    private List<DetectionObject> objects;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetectionObject {
        private Long trackId;
        private String label;
        private List<Integer> bbox;
        private Double confidence;
    }
}