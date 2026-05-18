package me.zonesafe.zonesafe_be.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.zonesafe.zonesafe_be.enums.AlarmSeverity;
import me.zonesafe.zonesafe_be.enums.AlarmType;

import java.time.ZonedDateTime;

@Entity
@Table(name = "video_analysis_events")
@Getter
@Setter
@NoArgsConstructor
public class VideoAnalysisEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long eventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", nullable = false)
    private Video video;

    @Column(length = 64)
    private String jobId;

    @Column(nullable = false)
    private Double frameTimestamp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlarmSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlarmType type;

    private Long roiId;
    private Long clipId;

    @Column(columnDefinition = "JSON")
    private String detectionsJson;

    @Column(nullable = false)
    private ZonedDateTime occurredAt;
}
