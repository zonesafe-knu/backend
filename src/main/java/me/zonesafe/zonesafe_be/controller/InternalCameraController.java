package me.zonesafe.zonesafe_be.controller;

import lombok.RequiredArgsConstructor;
import me.zonesafe.zonesafe_be.dto.CameraResponseDto;
import me.zonesafe.zonesafe_be.dto.CameraStatusUpdateRequest;
import me.zonesafe.zonesafe_be.service.CameraService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/cameras")
@RequiredArgsConstructor
public class InternalCameraController {

    private final CameraService cameraService;

    @PatchMapping("/{cameraId}/status")
    @ResponseStatus(HttpStatus.OK)
    public CameraResponseDto updateCameraStatus(
            @PathVariable Long cameraId,
            @RequestBody CameraStatusUpdateRequest request
    ) {
        return cameraService.updateCameraStatus(cameraId, request);
    }
}