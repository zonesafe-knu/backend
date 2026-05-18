package me.zonesafe.zonesafe_be.dto;

import lombok.Getter;
import lombok.Setter;
import me.zonesafe.zonesafe_be.enums.CameraStatus;

import java.time.ZonedDateTime;

@Getter
@Setter
public class CameraStatusUpdateRequest {
    private CameraStatus status;
    private ZonedDateTime lastHeartbeat;
}