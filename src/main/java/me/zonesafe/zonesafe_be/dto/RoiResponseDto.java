package me.zonesafe.zonesafe_be.dto;

import lombok.Getter;
import lombok.Setter;
import me.zonesafe.zonesafe_be.enums.AlarmRule;

import java.time.ZonedDateTime;

@Getter
@Setter
public class RoiResponseDto {
    private Long roiId;
    private Long cameraId;
    private String name;
    private int[][] polygon;
    private AlarmRule alarmRule;
    private Boolean muteForkliftOnly;
    private Integer dangerDistanceThreshold;
    private Boolean active;
    private ZonedDateTime createdAt;
}