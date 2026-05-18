package me.zonesafe.zonesafe_be.dto;

import lombok.Getter;
import lombok.Setter;
import me.zonesafe.zonesafe_be.enums.AlarmSeverity;
import me.zonesafe.zonesafe_be.enums.AlarmType;

import java.time.ZonedDateTime;

@Getter
@Setter
public class AlarmCreateRequest {
    private Long cameraId;
    private Long roiId;
    private AlarmSeverity severity;
    private AlarmType type;
    private String message;
    private String snapshotUrl;
    private String detectionsJson;
    private Long clipId;
    private ZonedDateTime occurredAt;
}