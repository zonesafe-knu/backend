package me.zonesafe.zonesafe_be.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zonesafe.zonesafe_be.domain.Alarm;
import me.zonesafe.zonesafe_be.domain.AlarmSpecification;
import me.zonesafe.zonesafe_be.domain.Camera;
import me.zonesafe.zonesafe_be.domain.Clip;
import me.zonesafe.zonesafe_be.dto.AlarmCreateRequest;
import me.zonesafe.zonesafe_be.dto.AlarmEvent;
import me.zonesafe.zonesafe_be.dto.AlarmResponseDto;
import me.zonesafe.zonesafe_be.dto.AlarmStatusChangedEvent;
import me.zonesafe.zonesafe_be.enums.AlarmSeverity;
import me.zonesafe.zonesafe_be.enums.AlarmStatus;
import me.zonesafe.zonesafe_be.enums.AlarmType;
import me.zonesafe.zonesafe_be.repository.AlarmRepository;
import me.zonesafe.zonesafe_be.repository.CameraRepository;
import me.zonesafe.zonesafe_be.repository.RoiRepository;

import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;


import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlarmService {
    private final AlarmRepository alarmRepository;
    private final CameraRepository cameraRepository;
    private final RoiRepository roiRepository;
    private final ModelMapper modelMapper;
    private final ObjectMapper objectMapper;
    private final AlarmEventPublisher alarmEventPublisher;
    private final ClipExtractionService clipExtractionService;

    @Transactional
    public AlarmResponseDto createAlarm(AlarmCreateRequest request) {
        Camera camera = cameraRepository.findById(request.getCameraId())
                .orElseThrow(() -> new RuntimeException("해당 카메라를 찾을 수 없습니다. ID: " + request.getCameraId()));

        Alarm alarm = new Alarm();
        alarm.setCamera(camera);
        alarm.setRoiId(request.getRoiId());
        alarm.setSeverity(request.getSeverity());
        alarm.setType(request.getType());
        alarm.setStatus(AlarmStatus.NEW);
        alarm.setMessage(request.getMessage());
        alarm.setDetectionsJson(request.getDetectionsJson());
        alarm.setClipId(request.getClipId());
        alarm.setSnapshotUrl(request.getSnapshotUrl());
        alarm.setOccurredAt(request.getOccurredAt() != null ? request.getOccurredAt() : ZonedDateTime.now());
        alarm.setVideoId(request.getVideoId());
        alarm.setVideoTimeSec(request.getVideoTimeSec());

        Alarm saved = alarmRepository.save(alarm);

        // 업로드 영상 분석에서 발생한 알람이면 원본 영상에서 ±N초 클립 자동 추출
        if (saved.getClipId() == null
                && request.getVideoId() != null
                && request.getVideoTimeSec() != null) {
            try {
                Clip clip = clipExtractionService.extractForAlarm(
                        saved, request.getVideoId(), request.getVideoTimeSec());
                if (clip != null) {
                    saved.setClipId(clip.getClipId()); // dirty checking 으로 UPDATE
                }
            } catch (Exception e) {
                log.warn("알람 클립 추출 중 예외 (알람은 정상 저장됨): alarmId={}, {}",
                        saved.getAlarmId(), e.getMessage());
            }
        }

        AlarmEvent event = AlarmEvent.builder()
                .alarmId(saved.getAlarmId())
                .cameraId(camera.getCameraId())
                .roiId(request.getRoiId())
                .severity(saved.getSeverity())
                .type(saved.getType())
                .message(saved.getMessage())
                .snapshotUrl(request.getSnapshotUrl())
                .occurredAt(saved.getOccurredAt())
                .videoId(saved.getVideoId())
                .videoTimeSec(saved.getVideoTimeSec())
                .build();
        alarmEventPublisher.publish(event);

        return convertToDto(saved);
    }

    public Page<AlarmResponseDto> getAlarms(
            Long cameraId, Long roiId, AlarmSeverity severity,
            AlarmType type, AlarmStatus status,
            ZonedDateTime from, ZonedDateTime to, Pageable pageable) {

        Specification<Alarm> spec = AlarmSpecification.filterAlarms(cameraId, roiId, severity, type, status, from, to);
        Page<Alarm> alarms = alarmRepository.findAll(spec, pageable);

        return alarms.map(this::convertToDto);
    }

    public AlarmResponseDto getAlarmById(Long alarmId) {
        //ID로 알람 엔티티 조회
        Alarm alarm = alarmRepository.findById(alarmId)
                .orElseThrow(()->new RuntimeException("해당 알람을 찾을 수 없습니다. ID: " + alarmId));

        return convertToDto(alarm);
    }

    @Transactional
    public AlarmResponseDto updateAlarmStatus(Long alarmId, AlarmStatus newStatus, String comment) {
        //ID로 알람 엔티티 조회
        Alarm alarm = alarmRepository.findById(alarmId)
                .orElseThrow(()->new RuntimeException("해당 알람을 찾을 수 없습니다. ID: " + alarmId));

        //사애 및 코멘트 업데이트
        alarm.setStatus(newStatus);
        alarm.setComment(comment);

        //Transaction 덕분에 .save(alarm)으로 DB에 자동으로 update

        alarmEventPublisher.publishStatusChange(AlarmStatusChangedEvent.builder()
                .alarmId(alarm.getAlarmId())
                .status(newStatus)
                .comment(comment)
                .changedAt(ZonedDateTime.now())
                .build());

        return convertToDto(alarm);
    }

    @Transactional
    public int bulkAckAlarms(List<Long> alarmIds) {
        //요청받은 ID 리스트에 해당하는 알람들을 DB에서 한번에 조회
        List<Alarm> alarms = alarmRepository.findAllById(alarmIds);

        ZonedDateTime now = ZonedDateTime.now();
        //조회된 알람들의 상태를 모두 ACK로 변경
        for(Alarm alarm : alarms) {
            alarm.setStatus(AlarmStatus.ACK);

            // (선택) 일괄 처리 시 남길 기본 코멘트가 있다면 세팅
            alarm.setComment("일괄 확인(ACK) 처리됨");

            alarmEventPublisher.publishStatusChange(AlarmStatusChangedEvent.builder()
                    .alarmId(alarm.getAlarmId())
                    .status(AlarmStatus.ACK)
                    .comment(alarm.getComment())
                    .changedAt(now)
                    .build());
        }

        return alarms.size();
    }

    private AlarmResponseDto convertToDto(Alarm alarm) {
        AlarmResponseDto dto = modelMapper.map(alarm, AlarmResponseDto.class);

        dto.setCameraId(alarm.getCamera().getCameraId());
        dto.setCameraName(alarm.getCamera().getName());

        dto.setSnapshotUrl(alarm.getSnapshotUrl());

        if (alarm.getRoiId() != null) {
            roiRepository.findById(alarm.getRoiId())
                    .ifPresent(roi -> dto.setRoiName(roi.getName()));
        }

        //JSON 문자열 -> List<DetectionDto> 변환
        if (alarm.getDetectionsJson() != null && !alarm.getDetectionsJson().isEmpty()) {
            try {
                List<AlarmResponseDto.DetectionDto> detections = objectMapper.readValue(
                        alarm.getDetectionsJson(), new TypeReference<List<AlarmResponseDto.DetectionDto>>() {});
                dto.setDetections(detections);
            } catch (JsonProcessingException e) {
                dto.setDetections(null);
            }
        }
        return dto;
    }

}
