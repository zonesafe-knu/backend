package me.zonesafe.zonesafe_be.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AlarmBulkAckRequest {
    private List<Long> alarmIds;
}
