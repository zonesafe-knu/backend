package me.zonesafe.zonesafe_be.dto;

import lombok.Getter;
import lombok.Setter;
import me.zonesafe.zonesafe_be.enums.VideoStatus;

import java.time.ZonedDateTime;

@Getter
@Setter
public class VideoResponseDto {
    private Long videoId;
    private String filename;
    private String displayName;
    private Long fileSize;
    private Integer duration;
    private String resolution;
    private Integer fps;
    private VideoStatus status;
    private Long siteId;
    private Long cameraContext;
    private String description;
    private String uploadedBy;
    private ZonedDateTime uploadedAt;
    private ZonedDateTime lastAnalyzedAt;
    private String downloadUrl;
    private String streamUrl;
    private String thumbnailUrl;
}
