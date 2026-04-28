package me.zonesafe.zonesafe_be.controller;

import lombok.RequiredArgsConstructor;
import me.zonesafe.zonesafe_be.dto.CameraRequestDto;
import me.zonesafe.zonesafe_be.dto.CameraResponseDto;
import me.zonesafe.zonesafe_be.enums.CameraStatus;
import me.zonesafe.zonesafe_be.service.CameraService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cameras")
@RequiredArgsConstructor
public class CameraController {
    private final CameraService cameraService;

    //카메라 등록
    @PostMapping
    public ResponseEntity<CameraResponseDto> createCamera(@RequestBody CameraRequestDto cameraRequestDto) {
        CameraResponseDto savedCamera = cameraService.createCamera(cameraRequestDto);

        return ResponseEntity.status(HttpStatus.CREATED).body(savedCamera);
    }

    //카메라 조회
    @GetMapping
    public ResponseEntity<List<CameraResponseDto>> getCameras(@RequestParam(required = false) Long siteId,
                                                              @RequestParam(required = false) CameraStatus status){
        List<CameraResponseDto> cameraList = cameraService.getCameras(siteId, status);
        return ResponseEntity.ok(cameraList);
    }

}
