package me.zonesafe.zonesafe_be.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import me.zonesafe.zonesafe_be.enums.AlarmSeverity;
import me.zonesafe.zonesafe_be.enums.AlarmType;
import me.zonesafe.zonesafe_be.enums.AnalysisJobStatus;

import java.time.ZonedDateTime;
import java.util.List;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VideoAnalysisJobStatusDto {
    private String jobId;
    private Long videoId;
    private AnalysisJobStatus status;

    private Double progress;
    private Integer framesProcessed;
    private Integer totalFrames;
    private Integer eventsDetected;
    private ZonedDateTime startedAt;
    private Integer estimatedRemainingSec;

    private ZonedDateTime completedAt;
    private Result result;

    @Getter
    @Setter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Result {
        private Integer totalEvents;
        private List<EventDto> events;
        private Metrics metrics;
    }

    @Getter
    @Setter
    public static class EventDto {
        private AlarmSeverity severity;
        private AlarmType type;
        private Double frameTimestamp;
        private ZonedDateTime occurredAt;
        private Long roiId;
        private Long clipId;
    }

    @Getter
    @Setter
    public static class Metrics {
        private Double avgInferenceMs;
        private String modelVersion;
        private Integer totalDetections;
    }
}
