package me.zonesafe.zonesafe_be.controller;

import lombok.RequiredArgsConstructor;
import me.zonesafe.zonesafe_be.dto.CameraRequestDto;
import me.zonesafe.zonesafe_be.dto.CameraResponseDto;
import me.zonesafe.zonesafe_be.dto.StreamResponseDto;
import me.zonesafe.zonesafe_be.dto.VideoAnalyzeJobResponseDto;
import me.zonesafe.zonesafe_be.enums.CameraStatus;
import me.zonesafe.zonesafe_be.service.CameraService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cameras")
@RequiredArgsConstructor
public class CameraController {
    private final CameraService cameraService;

    //카메라 등록
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CameraResponseDto createCamera(@RequestBody CameraRequestDto cameraRequestDto) {
        return cameraService.createCamera(cameraRequestDto);
    }

    //카메라 조회
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<CameraResponseDto> getCameras(@RequestParam(required = false) Long siteId,
                                              @RequestParam(required = false) CameraStatus status){
        return cameraService.getCameras(siteId, status);
    }

    //카메라 상세 조회
    @GetMapping("/{cameraId}")
    @ResponseStatus(HttpStatus.OK)
    public CameraResponseDto getCameraById(@PathVariable Long cameraId) {
        return cameraService.getCameraById(cameraId);
    }

    //카메라 삭제
    @DeleteMapping("/{cameraId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCamera(@PathVariable Long cameraId) {
        cameraService.deleteCamera(cameraId);
    }

    //카메라 수정
    @PutMapping("/{cameraId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateCamera(@PathVariable Long cameraId, @RequestBody CameraRequestDto cameraRequestDto) {
        cameraService.updateCamera(cameraId, cameraRequestDto);
    }

    ////실시간 스트림 URL 발급(RTSP -> HLS)
    @GetMapping("/{cameraId}/stream")
    @ResponseStatus(HttpStatus.OK)
    public StreamResponseDto getStreamUrl(@PathVariable Long cameraId) {
        return cameraService.getStreamUrl(cameraId);
    }

    //카메라 화면 접속 시 YOLO 분석 시작
    @PostMapping("/{cameraId}/stream/start")
    @ResponseStatus(HttpStatus.OK)
    public VideoAnalyzeJobResponseDto startCameraStream(@PathVariable Long cameraId) {
        return cameraService.startCameraStream(cameraId);
    }
}
