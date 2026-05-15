package me.zonesafe.zonesafe_be.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import me.zonesafe.zonesafe_be.enums.AlarmSeverity;
import me.zonesafe.zonesafe_be.enums.AlarmType;

import java.time.ZonedDateTime;
import java.util.List;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VideoEventResponseDto {
    private Long eventId;
    private Long videoId;
    private Double frameTimestamp;
    private AlarmSeverity severity;
    private AlarmType type;
    private Long roiId;
    private Long clipId;
    private ZonedDateTime occurredAt;
    private List<DetectionDto> detections;

    @Getter
    @Setter
    public static class DetectionDto {
        private String label;
        private Long trackId;
        private List<Integer> bbox;
        private Double confidence;
    }
}
