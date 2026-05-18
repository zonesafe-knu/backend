package me.zonesafe.zonesafe_be.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import me.zonesafe.zonesafe_be.enums.VideoStatus;

import java.time.ZonedDateTime;
import java.util.List;

@Getter
@Setter
public class VideoResponseDto {
    private Long videoId;
    private String filename;
    private String displayName;
    private Long fileSize;
    private Integer duration;
    private String resolution;
    private Integer fps;
    private VideoStatus status;
    private Long siteId;
    private Long cameraContext;
    private String description;
    private String uploadedBy;
    private ZonedDateTime uploadedAt;
    private ZonedDateTime lastAnalyzedAt;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private AnalysisResults analysisResults;

    private String downloadUrl;
    private String streamUrl;
    private String thumbnailUrl;

    @Getter
    @Setter
    public static class AnalysisResults {
        private Integer totalEvents;
        private Integer dangerCount;
        private Integer warnCount;
        private Integer infoCount;
        private List<Long> generatedClipIds;
    }
}
