package me.zonesafe.zonesafe_be.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.zonesafe.zonesafe_be.enums.AlarmSeverity;
import me.zonesafe.zonesafe_be.enums.AlarmType;

import java.time.ZonedDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmEvent {
    private Long alarmId;
    private Long cameraId;
    private Long roiId;
    private AlarmSeverity severity;
    private AlarmType type;
    private String message;
    private String snapshotUrl;
    private ZonedDateTime occurredAt;
    private Long videoId;
    private Double videoTimeSec;
}