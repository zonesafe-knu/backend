package me.zonesafe.zonesafe_be.dto;

import lombok.Getter;
import lombok.Setter;
import me.zonesafe.zonesafe_be.enums.AutoLabelJobStatus;

@Getter
@Setter
public class AutoLabelJobResponseDto {
    private String jobId;
    private AutoLabelJobStatus status;
    private Double progress;
    private Integer totalImages;
    private Integer processed;
}