package me.zonesafe.zonesafe_be.controller;

import lombok.RequiredArgsConstructor;
import me.zonesafe.zonesafe_be.dto.CameraResponseDto;
import me.zonesafe.zonesafe_be.enums.CameraStatus;
import me.zonesafe.zonesafe_be.service.CameraService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cameras")
@RequiredArgsConstructor
public class CameraController {
    private final CameraService cameraService;

    //카메라 조회
    @GetMapping
    public ResponseEntity<List<CameraResponseDto>> getCameras(@RequestParam(required = false) Long siteId,
                                                              @RequestParam(required = false) CameraStatus status){
        List<CameraResponseDto> cameraList = cameraService.getCameras(siteId, status);
        return ResponseEntity.ok(cameraList);
    }

}
