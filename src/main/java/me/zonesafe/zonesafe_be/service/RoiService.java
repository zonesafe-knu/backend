package me.zonesafe.zonesafe_be.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zonesafe.zonesafe_be.domain.Camera;
import me.zonesafe.zonesafe_be.domain.Roi;
import me.zonesafe.zonesafe_be.domain.Video;
import me.zonesafe.zonesafe_be.dto.RoiRequestDto;
import me.zonesafe.zonesafe_be.dto.RoiResponseDto;
import me.zonesafe.zonesafe_be.repository.CameraRepository;
import me.zonesafe.zonesafe_be.repository.RoiRepository;
import me.zonesafe.zonesafe_be.repository.VideoRepository;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoiService {
    private final RoiRepository roiRepository;
    private final CameraRepository cameraRepository;
    private final VideoRepository videoRepository;
    private final VideoAnalysisService videoAnalysisService;
    private final ModelMapper modelMapper;
    private final ObjectMapper objectMapper;

    //ROI 목록 조회
    public List<RoiResponseDto> getRois(Long cameraId) {
        List<Roi> rois = (cameraId != null)
                ? roiRepository.findAllByCamera_CameraId(cameraId)
                : roiRepository.findAll();

        return rois.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    //ROI 상세 조회
    public RoiResponseDto getRoiById(Long roiId) {
        Roi roi = roiRepository.findById(roiId)
                .orElseThrow(() -> new IllegalArgumentException("해당 ROI가 존재하지 않습니다. ID: " + roiId));

        return convertToDto(roi);
    }

    //ROI 생성
    @Transactional
    public RoiResponseDto createRoi(RoiRequestDto requestDto) {
        Camera camera = cameraRepository.findById(requestDto.getCameraId())
                .orElseThrow(() -> new IllegalArgumentException("해당 카메라가 존재하지 않습니다. ID: " + requestDto.getCameraId()));

        Roi roi = new Roi();
        roi.setCamera(camera);
        roi.setName(requestDto.getName());
        roi.setPolygonJson(serializePolygon(requestDto.getPolygon()));
        roi.setAlarmRule(requestDto.getAlarmRule());
        roi.setMuteForkliftOnly(requestDto.getMuteForkliftOnly() != null ? requestDto.getMuteForkliftOnly() : true);
        roi.setDangerDistanceThreshold(requestDto.getDangerDistanceThreshold());
        roi.setReferenceWidth(requestDto.getReferenceWidth());
        roi.setReferenceHeight(requestDto.getReferenceHeight());
        roi.setActive(requestDto.getActive() != null ? requestDto.getActive() : true);

        Roi saved = roiRepository.save(roi);

        //ROI 등록 시 해당 카메라에 영상이 있으면 자동 분석 시작
        List<Video> videos = videoRepository.findAllByCameraContext(camera.getCameraId());
        if (!videos.isEmpty()) {
            try {
                videoAnalysisService.startAnalysis(videos.get(0).getVideoId(), null);
            } catch (Exception e) {
                log.warn("ROI 등록 후 자동 분석 시작 실패 — cameraId={}, reason={}",
                        camera.getCameraId(), e.getMessage());
            }
        }

        return convertToDto(saved);
    }

    //ROI 수정
    @Transactional
    public RoiResponseDto updateRoi(Long roiId, RoiRequestDto requestDto) {
        Roi roi = roiRepository.findById(roiId)
                .orElseThrow(() -> new IllegalArgumentException("수정하려는 ROI가 존재하지 않습니다. ID: " + roiId));

        if (requestDto.getCameraId() != null
                && !requestDto.getCameraId().equals(roi.getCamera().getCameraId())) {
            Camera camera = cameraRepository.findById(requestDto.getCameraId())
                    .orElseThrow(() -> new IllegalArgumentException("해당 카메라가 존재하지 않습니다. ID: " + requestDto.getCameraId()));
            roi.setCamera(camera);
        }

        roi.setName(requestDto.getName());
        roi.setPolygonJson(serializePolygon(requestDto.getPolygon()));
        roi.setAlarmRule(requestDto.getAlarmRule());
        if (requestDto.getMuteForkliftOnly() != null) roi.setMuteForkliftOnly(requestDto.getMuteForkliftOnly());
        roi.setDangerDistanceThreshold(requestDto.getDangerDistanceThreshold());
        if (requestDto.getReferenceWidth() != null) roi.setReferenceWidth(requestDto.getReferenceWidth());
        if (requestDto.getReferenceHeight() != null) roi.setReferenceHeight(requestDto.getReferenceHeight());
        if (requestDto.getActive() != null) roi.setActive(requestDto.getActive());

        return convertToDto(roi);
    }

    //ROI 활성화 토글
    @Transactional
    public RoiResponseDto toggleActive(Long roiId) {
        Roi roi = roiRepository.findById(roiId)
                .orElseThrow(() -> new IllegalArgumentException("해당 ROI가 존재하지 않습니다. ID: " + roiId));

        roi.setActive(!Boolean.TRUE.equals(roi.getActive()));
        return convertToDto(roi);
    }

    //ROI 삭제
    @Transactional
    public void deleteRoi(Long roiId) {
        Roi roi = roiRepository.findById(roiId)
                .orElseThrow(() -> new IllegalArgumentException("삭제하려는 ROI가 존재하지 않습니다. ID: " + roiId));

        roiRepository.delete(roi);
    }

    private RoiResponseDto convertToDto(Roi roi) {
        RoiResponseDto dto = modelMapper.map(roi, RoiResponseDto.class);
        dto.setCameraId(roi.getCamera().getCameraId());
        dto.setPolygon(deserializePolygon(roi.getPolygonJson()));
        return dto;
    }

    private String serializePolygon(int[][] polygon) {
        if (polygon == null) return null;
        try {
            return objectMapper.writeValueAsString(polygon);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("polygon 직렬화에 실패했습니다.", e);
        }
    }

    private int[][] deserializePolygon(String polygonJson) {
        if (polygonJson == null || polygonJson.isEmpty()) return null;
        try {
            return objectMapper.readValue(polygonJson, int[][].class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}