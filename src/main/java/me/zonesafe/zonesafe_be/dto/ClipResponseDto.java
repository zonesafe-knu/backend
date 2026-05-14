package me.zonesafe.zonesafe_be.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Getter
@Setter
public class ClipResponseDto {
    private Long clipId;
    private Long alarmId;
    private Long cameraId;
    private Integer duration;
    private Long fileSize;
    private String format;
    private String downloadUrl;
    private String streamUrl;
    private String thumbnailUrl;
    private ZonedDateTime occurredAt;
    private ZonedDateTime startAt;
    private ZonedDateTime endAt;
}
