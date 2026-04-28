package me.zonesafe.zonesafe_be.domain;

import lombok.Setter;
import me.zonesafe.zonesafe_be.enums.CameraStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.ZonedDateTime;

@Entity
@Table(name = "cameras")
@Getter
@Setter
@NoArgsConstructor
public class Camera {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long cameraId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String rtspUrl;

    @Column(nullable = false)
    private Long siteId;

    @Column(nullable = false)
    private String siteName;

    private String resolution;
    private Integer fps;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CameraStatus status;

    private ZonedDateTime lastHeartbeat;
}