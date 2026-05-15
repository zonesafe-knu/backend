package me.zonesafe.zonesafe_be.dto;

import lombok.Getter;
import lombok.Setter;
import me.zonesafe.zonesafe_be.enums.AnalysisJobStatus;

@Getter
@Setter
public class VideoAnalyzeJobResponseDto {
    private String jobId;
    private Long videoId;
    private AnalysisJobStatus status;
}
