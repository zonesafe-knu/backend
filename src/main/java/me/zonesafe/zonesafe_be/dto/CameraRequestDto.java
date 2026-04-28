package me.zonesafe.zonesafe_be.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CameraRequestDto {
    private String name;
    private String rtspUrl;
    private Long siteId;
    private String resolution;
    private Integer fps;
}
