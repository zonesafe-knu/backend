package me.zonesafe.zonesafe_be.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import me.zonesafe.zonesafe_be.domain.Alarm;
import me.zonesafe.zonesafe_be.domain.AlarmSpecification;
import me.zonesafe.zonesafe_be.dto.AlarmResponseDto;
import me.zonesafe.zonesafe_be.enums.AlarmSeverity;
import me.zonesafe.zonesafe_be.enums.AlarmStatus;
import me.zonesafe.zonesafe_be.enums.AlarmType;
import me.zonesafe.zonesafe_be.repository.AlarmRepository;

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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlarmService {
    private final AlarmRepository alarmRepository;
    private final ModelMapper modelMapper;
    private final ObjectMapper objectMapper;

    public Page<AlarmResponseDto> getAlarms(
            Long cameraId, AlarmSeverity severity,
            AlarmType type, AlarmStatus status,
            ZonedDateTime from, ZonedDateTime to, Pageable pageable) {

        Specification<Alarm> spec = AlarmSpecification.filterAlarms(cameraId, severity, type, status, from, to);
        Page<Alarm> alarms = alarmRepository.findAll(spec, pageable);

        return alarms.map(this::convertToDto);
    }

    private AlarmResponseDto convertToDto(Alarm alarm) {
        AlarmResponseDto dto = modelMapper.map(alarm, AlarmResponseDto.class);

        dto.setCameraId(alarm.getCamera().getCameraId());
        dto.setCameraName(alarm.getCamera().getName());

        //ROI 세팅 추가
        //snapshop 추가

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
