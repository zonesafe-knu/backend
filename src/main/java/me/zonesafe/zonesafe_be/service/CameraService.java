package me.zonesafe.zonesafe_be.service;

import lombok.RequiredArgsConstructor;
import me.zonesafe.zonesafe_be.domain.Camera;
import me.zonesafe.zonesafe_be.dto.CameraRequestDto;
import me.zonesafe.zonesafe_be.dto.CameraResponseDto;
import me.zonesafe.zonesafe_be.enums.CameraStatus;
import me.zonesafe.zonesafe_be.repository.CameraRepository;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CameraService {
    private final CameraRepository cameraRepository;
    private final ModelMapper modelMapper;

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
}
