package me.zonesafe.zonesafe_be.dto;

import lombok.Getter;
import lombok.Setter;
import me.zonesafe.zonesafe_be.enums.ModelFormat;

import java.time.ZonedDateTime;

@Getter
@Setter
public class ModelResponseDto {
    private Long modelId;
    private String name;
    private String version;
    private ModelFormat format;
    private Double mAP50;
    private Double mAP50_95;
    private Double fps;
    private Boolean active;
    private ZonedDateTime createdAt;
}