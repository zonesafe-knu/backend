package me.zonesafe.zonesafe_be.service;

import lombok.RequiredArgsConstructor;
import me.zonesafe.zonesafe_be.domain.Camera;
import me.zonesafe.zonesafe_be.dto.CameraRequestDto;
import me.zonesafe.zonesafe_be.dto.CameraResponseDto;
import me.zonesafe.zonesafe_be.dto.CameraStatusEvent;
import me.zonesafe.zonesafe_be.dto.CameraStatusUpdateRequest;
import me.zonesafe.zonesafe_be.dto.StreamResponseDto;
import me.zonesafe.zonesafe_be.enums.CameraStatus;
import me.zonesafe.zonesafe_be.repository.CameraRepository;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CameraService {
    private final CameraRepository cameraRepository;
    private final ModelMapper modelMapper;
    private final CameraEventPublisher cameraEventPublisher;

    //카메라 조회
    public List<CameraResponseDto> getCameras(Long siteId, CameraStatus status) {
        List<Camera> cameras;

        if (siteId != null && status != null) {
            cameras = cameraRepository.findAllBySiteIdAndStatus(siteId, status);
        } else if (siteId != null) {
            cameras = cameraRepository.findAllBySiteId(siteId);
        } else if (status != null) {
            cameras = cameraRepository.findAllByStatus(status);
        } else {
            cameras = cameraRepository.findAll();
        }

        return cameras.stream()
                .map(camera-> modelMapper.map(camera, CameraResponseDto.class))
                .collect(Collectors.toList());
    }

    //카메라 상세 조회
    public CameraResponseDto getCameraById(Long cameraId) {
        Camera camera = cameraRepository.findById(cameraId)
                .orElseThrow(() -> new IllegalArgumentException("해당 카메라가 존재하지 않습니다."));

        return modelMapper.map(camera, CameraResponseDto.class);
    }

    //카메라 등록
    @Transactional
    public CameraResponseDto createCamera(CameraRequestDto cameraRequestDto) {
        //DTO -> Entity변환
        Camera camera = modelMapper.map(cameraRequestDto, Camera.class);

        //초기상태 등록
        camera.setStatus(CameraStatus.OFFLINE);

        //DB저장
        Camera savedCamera = cameraRepository.save(camera);

        //저장된 Entity -> ResponseDto 변환 후 반환
        return modelMapper.map(savedCamera, CameraResponseDto.class);
    }

    //카메라 삭제
    @Transactional
    public void deleteCamera(Long cameraId) {
        //삭제할 카메라가 있는지 확인
        Camera camera = cameraRepository.findById(cameraId)
                .orElseThrow(()->new IllegalArgumentException("삭제하려는 카메라가 존재하지 않습니다. ID: " + cameraId));

        cameraRepository.delete(camera);
    }

    //카메라 수정
    @Transactional
    public void updateCamera(Long cameraId, CameraRequestDto cameraRequestDto) {
        //수정할 카메라 존재 확인
        Camera camera = cameraRepository.findById(cameraId)
                .orElseThrow(()->new IllegalArgumentException("수정하려는 카메라가 존재하지 않습니다. ID: " + cameraId));

        CameraStatus previousStatus = camera.getStatus();

        //정보 수정
        camera.setName(cameraRequestDto.getName());
        camera.setRtspUrl(cameraRequestDto.getRtspUrl());
        camera.setSiteId(cameraRequestDto.getSiteId());
        camera.setSiteName(cameraRequestDto.getSiteName());
        camera.setResolution(cameraRequestDto.getResolution());
        camera.setFps(cameraRequestDto.getFps());
        camera.setStatus(cameraRequestDto.getStatus());

        if (cameraRequestDto.getStatus() != null && cameraRequestDto.getStatus() != previousStatus) {
            publishStatusEvent(camera);
        }
    }

    //카메라 상태 단독 업데이트 (heartbeat / AI 파이프라인 등 내부 호출용)
    @Transactional
    public CameraResponseDto updateCameraStatus(Long cameraId, CameraStatusUpdateRequest request) {
        Camera camera = cameraRepository.findById(cameraId)
                .orElseThrow(() -> new IllegalArgumentException("카메라를 찾을 수 없습니다. ID: " + cameraId));

        CameraStatus previousStatus = camera.getStatus();
        if (request.getStatus() != null) {
            camera.setStatus(request.getStatus());
        }
        if (request.getLastHeartbeat() != null) {
            camera.setLastHeartbeat(request.getLastHeartbeat());
        }

        boolean statusChanged = request.getStatus() != null && request.getStatus() != previousStatus;
        if (statusChanged) {
            publishStatusEvent(camera);
        }

        return modelMapper.map(camera, CameraResponseDto.class);
    }

    private void publishStatusEvent(Camera camera) {
        CameraStatusEvent event = CameraStatusEvent.builder()
                .cameraId(camera.getCameraId())
                .status(camera.getStatus())
                .lastHeartbeat(camera.getLastHeartbeat())
                .changedAt(ZonedDateTime.now())
                .build();
        cameraEventPublisher.publishStatus(event);
    }

    public StreamResponseDto getStreamUrl(Long cameraId) {
        //카메라 존재 확인
        Camera camera = cameraRepository.findById(cameraId)
                .orElseThrow(()->new IllegalArgumentException("카메라를 찾을 수 없습니다. ID: " + cameraId));

        //미디어 서버 주소 규칙에 따라 URL 생성
        String hlsUrl = "https://zonesafe-stream.com/live/cam" + cameraId + "/index.m3u8";
        return new StreamResponseDto(hlsUrl, "HLS");
    }
}
