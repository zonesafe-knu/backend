package me.zonesafe.zonesafe_be.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.zonesafe.zonesafe_be.enums.AlarmSeverity;
import me.zonesafe.zonesafe_be.enums.AlarmStatus;
import me.zonesafe.zonesafe_be.enums.AlarmType;

import java.time.ZonedDateTime;

@Entity
@Table(name = "alarms")
@Getter
@Setter
@NoArgsConstructor
public class Alarm {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long alarmId;

    // 외래키 설정 1: Camera 참조
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "camera_id", nullable = false)
    private Camera camera;

//    // 외래키 설정 2: Roi 참조 (nullable, 명세서 반영)
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "roi_id")
//    private Roi roi;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlarmSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlarmType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlarmStatus status;

    private String message;

    @Column(columnDefinition = "JSON")
    private String detectionsJson; // 명세서의 json 타입 반영 (String으로 우선 매핑)

    private Long clipId; // nullable

    @Column(length = 500)
    private String comment;

    @Column(nullable = false)
    private ZonedDateTime occurredAt;

    public void updateStatus(AlarmStatus newStatus, String comment) {
        this.status = newStatus;
        this.comment = comment;
    }
}