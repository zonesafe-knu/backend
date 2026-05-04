package me.zonesafe.zonesafe_be.domain;

import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class Detection {
    private String label;
    private Long trackId;
    private String bbox; // 예: "[340,210,420,470]" 형태로 저장 (또는 DB의 JSON 타입 활용)
    private Double confidence;
}
