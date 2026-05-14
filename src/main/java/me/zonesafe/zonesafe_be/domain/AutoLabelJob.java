package me.zonesafe.zonesafe_be.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.zonesafe.zonesafe_be.enums.AutoLabelJobStatus;

import java.time.ZonedDateTime;

@Entity
@Table(name = "auto_label_jobs")
@Getter
@Setter
@NoArgsConstructor
public class AutoLabelJob {
    @Id
    @Column(length = 64)
    private String jobId;

    @Column(nullable = false)
    private Long modelId;

    @Column(nullable = false)
    private Long imageSetId;

    @Column(nullable = false)
    private Double confidenceThreshold;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AutoLabelJobStatus status;

    @Column(nullable = false)
    private Double progress = 0.0;

    @Column(nullable = false)
    private Integer totalImages = 0;

    @Column(nullable = false)
    private Integer processed = 0;

    @Column(nullable = false)
    private ZonedDateTime createdAt;
}