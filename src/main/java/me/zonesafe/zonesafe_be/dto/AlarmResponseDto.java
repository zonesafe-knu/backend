package me.zonesafe.zonesafe_be.dto;

import lombok.Getter;
import lombok.Setter;
import me.zonesafe.zonesafe_be.enums.AlarmSeverity;
import me.zonesafe.zonesafe_be.enums.AlarmStatus;
import me.zonesafe.zonesafe_be.enums.AlarmType;

import java.time.ZonedDateTime;
import java.util.List;

@Getter
@Setter
public class AlarmResponseDto {
    private Long alarmId;

    private Long cameraId;
    private String cameraName;

    //roi 정보 추가해야 함.
    private Long roiId;
    private String roiName;
    private String snapshotUrl;

    private AlarmSeverity severity;
    private AlarmType type;
    private AlarmStatus status;
    private String message;

    private String comment;

    // detectionsJson(String)을 List로 변환해서 응답하기 위한 필드
    private List<DetectionDto> detections;

    private Long clipId;
    //스냅샷 url 추가해야함
    private ZonedDateTime occurredAt;

    @Getter
    @Setter
    public static class DetectionDto {
        private String label;
        private Long trackId;
        private List<Integer> bbox;
        private Double confidence;
    }


}
