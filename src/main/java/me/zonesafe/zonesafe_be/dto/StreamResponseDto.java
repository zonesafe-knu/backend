package me.zonesafe.zonesafe_be.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StreamResponseDto {
    private String streamUrl;
    private String type;    //"HLS"
}
