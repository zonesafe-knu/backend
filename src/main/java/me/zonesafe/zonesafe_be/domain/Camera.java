package me.zonesafe.zonesafe_be.domain;

import me.zonesafe.zonesafe_be.enums.CameraStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.ZonedDateTime;

@Entity
@Table(name = "cameras")
@Getter
@NoArgsConstructor
public class Camera {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long cameraId;

    private String name;
    private String rtspUrl;
    private Long siteId;
    private String siteName;
    private String resolution;
    private Integer fps;

    @Enumerated(EnumType.STRING)
    private CameraStatus status;

    private ZonedDateTime lastHeartbeat;
}