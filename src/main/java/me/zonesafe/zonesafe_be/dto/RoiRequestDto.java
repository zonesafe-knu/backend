package me.zonesafe.zonesafe_be.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.zonesafe.zonesafe_be.enums.AlarmRule;

@Getter
@Setter
@NoArgsConstructor
public class RoiRequestDto {
    private Long cameraId;
    private String name;
    private int[][] polygon;
    private AlarmRule alarmRule;
    private Boolean muteForkliftOnly;
    private Integer dangerDistanceThreshold;
    private Integer referenceWidth;
    private Integer referenceHeight;
    private Boolean active;
}