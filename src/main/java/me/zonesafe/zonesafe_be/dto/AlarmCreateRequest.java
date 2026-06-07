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

    // 업로드된 영상 분석 중 발생한 알람의 경우, 클립 추출용 메타데이터
    private Long videoId;
    private Double videoTimeSec;
}