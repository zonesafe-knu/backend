package me.zonesafe.zonesafe_be.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class VideoAnalyzeRequestDto {
    private Long modelId;
    private List<Long> roiIds;
    private Boolean saveClips;
    private Integer skipFrames;
    private AlarmRuleOverride alarmRuleOverride;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class AlarmRuleOverride {
        private Integer dangerDistanceThreshold;
        private Boolean muteForkliftOnly;
    }
}
