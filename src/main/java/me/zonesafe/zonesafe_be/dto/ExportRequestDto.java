package me.zonesafe.zonesafe_be.dto;

import lombok.Getter;
import lombok.Setter;
import me.zonesafe.zonesafe_be.enums.ModelFormat;

@Getter
@Setter
public class ExportRequestDto {
    private ModelFormat format;
}