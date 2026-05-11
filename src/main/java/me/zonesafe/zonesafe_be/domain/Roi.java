package me.zonesafe.zonesafe_be.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.zonesafe.zonesafe_be.enums.AlarmRule;

import java.time.ZonedDateTime;

@Entity
@Table(name = "rois")
@Getter
@Setter
@NoArgsConstructor
public class Roi {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long roiId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "camera_id", nullable = false)
    private Camera camera;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "JSON", nullable = false)
    private String polygonJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlarmRule alarmRule;

    @Column(nullable = false)
    private Boolean muteForkliftOnly;

    private Integer dangerDistanceThreshold;

    @Column(nullable = false)
    private Boolean active;

    @Column(nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now();
        if (this.muteForkliftOnly == null) this.muteForkliftOnly = true;
        if (this.active == null) this.active = true;
    }
}