package me.zonesafe.zonesafe_be.dto;

import lombok.Getter;
import lombok.Setter;
import me.zonesafe.zonesafe_be.enums.CameraStatus;

import java.time.ZonedDateTime;

@Getter
@Setter
public class CameraResponseDto {
    private Long cameraId;
    private String name;
    private String rtspUrl;
    private Long siteId;
    private String siteName;
    private String resolution;
    private Integer fps;
    private CameraStatus status;
    private ZonedDateTime lastHeartbeat;
}
