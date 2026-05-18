package me.zonesafe.zonesafe_be.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AlarmAckMessage {
    private Long alarmId;
}