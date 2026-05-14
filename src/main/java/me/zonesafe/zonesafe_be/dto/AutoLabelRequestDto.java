package me.zonesafe.zonesafe_be.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AutoLabelRequestDto {
    private Long modelId;
    private Long imageSetId;
    private Double confidenceThreshold;
}