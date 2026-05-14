package me.zonesafe.zonesafe_be.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.zonesafe.zonesafe_be.enums.ModelFormat;

import java.time.ZonedDateTime;

@Entity
@Table(name = "models")
@Getter
@Setter
@NoArgsConstructor
public class Model {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long modelId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ModelFormat format;

    @Column(name = "map_50")
    private Double mAP50;

    @Column(name = "map_50_95")
    private Double mAP50_95;

    private Double fps;

    @Column(nullable = false)
    private Boolean active = false;

    @Column(nullable = false)
    private ZonedDateTime createdAt;
}