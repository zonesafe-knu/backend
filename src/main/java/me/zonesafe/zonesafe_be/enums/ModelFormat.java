package me.zonesafe.zonesafe_be.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ModelFormat {
    PYTORCH("PyTorch"),
    ONNX("ONNX"),
    TENSORRT("TensorRT");

    private final String label;

    ModelFormat(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static ModelFormat from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("format 값이 비어있습니다.");
        }
        for (ModelFormat f : values()) {
            if (f.label.equalsIgnoreCase(value) || f.name().equalsIgnoreCase(value)) {
                return f;
            }
        }
        throw new IllegalArgumentException("지원하지 않는 모델 포맷입니다: " + value);
    }
}