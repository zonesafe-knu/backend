package me.zonesafe.zonesafe_be.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.zonesafe.zonesafe_be.enums.CameraStatus;

@Setter
@Getter
@NoArgsConstructor
public class CameraRequestDto {
    private String name;
    private String rtspUrl;
    private Long siteId;
    private String siteName;
    private String resolution;
    private Integer fps;
    private CameraStatus status;
}
