package me.zonesafe.zonesafe_be.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.zonesafe.zonesafe_be.enums.AnalysisJobStatus;

import java.time.ZonedDateTime;

@Entity
@Table(name = "video_analysis_jobs")
@Getter
@Setter
@NoArgsConstructor
public class VideoAnalysisJob {
    @Id
    @Column(length = 64)
    private String jobId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", nullable = false)
    private Video video;

    private Long modelId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisJobStatus status;

    @Column(nullable = false)
    private Double progress;

    private Integer framesProcessed;
    private Integer totalFrames;
    private Integer eventsDetected;

    @Column(columnDefinition = "JSON")
    private String resultJson;

    private ZonedDateTime startedAt;
    private ZonedDateTime completedAt;
    private Integer estimatedRemainingSec;

    @Column(nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now();
        if (this.status == null) this.status = AnalysisJobStatus.QUEUED;
        if (this.progress == null) this.progress = 0.0;
    }
}
