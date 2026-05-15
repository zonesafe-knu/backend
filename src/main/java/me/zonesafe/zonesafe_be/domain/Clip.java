package me.zonesafe.zonesafe_be.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Table(name = "clips")
@Getter
@Setter
@NoArgsConstructor
public class Clip {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long clipId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "camera_id", nullable = false)
    private Camera camera;

    @Column(nullable = false)
    private Long alarmId;

    @Column(nullable = false)
    private Integer duration;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false)
    private String format;

    @Column(nullable = false)
    private String filePath;

    private String thumbnailPath;

    @Column(nullable = false)
    private ZonedDateTime occurredAt;

    @Column(nullable = false)
    private ZonedDateTime startAt;

    @Column(nullable = false)
    private ZonedDateTime endAt;
}
