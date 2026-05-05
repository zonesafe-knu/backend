package me.zonesafe.zonesafe_be.domain;

import lombok.Setter;
import me.zonesafe.zonesafe_be.enums.CameraStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

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

    // 2. Alarm과의 양방향 연관관계 (카메라에서 알람 목록 조회 가능)
    @OneToMany(mappedBy = "camera", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Alarm> alarms = new ArrayList<>();

//    // 3. (보너스) Roi와의 양방향 관계도 동일한 원리로 추가해두면 관리가 편합니다.
//    @OneToMany(mappedBy = "camera", cascade = CascadeType.ALL, orphanRemoval = true)
//    private List<Roi> rois = new ArrayList<>();
}