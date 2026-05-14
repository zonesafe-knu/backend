package me.zonesafe.zonesafe_be.controller;

import lombok.RequiredArgsConstructor;
import me.zonesafe.zonesafe_be.dto.DetectionSnapshotResponseDto;
import me.zonesafe.zonesafe_be.service.DetectionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/detections")
@RequiredArgsConstructor
public class DetectionController {
    private final DetectionService detectionService;

    @GetMapping("/latest")
    @ResponseStatus(HttpStatus.OK)
    public DetectionSnapshotResponseDto getLatestDetectionSnapshot(@RequestParam Long cameraId) {
        return detectionService.getDetectionSnapshot(cameraId);
    }
}
