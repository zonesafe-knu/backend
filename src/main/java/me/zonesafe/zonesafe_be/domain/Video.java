package me.zonesafe.zonesafe_be.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.zonesafe.zonesafe_be.enums.VideoStatus;

import java.time.ZonedDateTime;

@Entity
@Table(name = "videos")
@Getter
@Setter
@NoArgsConstructor
public class Video {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long videoId;

    @Column(nullable = false)
    private String filename;

    private String displayName;

    @Column(nullable = false)
    private String filePath;

    private String thumbnailPath;

    @Column(nullable = false)
    private Long fileSize;

    private Integer duration;
    private String resolution;
    private Integer fps;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VideoStatus status;

    private Long siteId;
    private Long cameraContext;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private String uploadedBy;

    @Column(nullable = false, updatable = false)
    private ZonedDateTime uploadedAt;

    private ZonedDateTime lastAnalyzedAt;

    @PrePersist
    protected void onCreate() {
        this.uploadedAt = ZonedDateTime.now();
        if (this.status == null) this.status = VideoStatus.UPLOADED;
    }
}
