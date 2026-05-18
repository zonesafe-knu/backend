package me.zonesafe.zonesafe_be.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.zonesafe.zonesafe_be.enums.CameraStatus;

import java.time.ZonedDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CameraStatusEvent {
    private Long cameraId;
    private CameraStatus status;
    private ZonedDateTime lastHeartbeat;
    private ZonedDateTime changedAt;
}