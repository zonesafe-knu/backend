package me.zonesafe.zonesafe_be.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.zonesafe.zonesafe_be.enums.AlarmStatus;

import java.time.ZonedDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmStatusChangedEvent {
    private Long alarmId;
    private AlarmStatus status;
    private String comment;
    private ZonedDateTime changedAt;
}