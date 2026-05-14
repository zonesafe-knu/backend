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

        return convertToDto(alarm);
    }

    @Transactional
    public int bulkAckAlarms(List<Long> alarmIds) {
        //요청받은 ID 리스트에 해당하는 알람들을 DB에서 한번에 조회
        List<Alarm> alarms = alarmRepository.findAllById(alarmIds);

        //조회된 알람들의 상태를 모두 ACK로 변경
        for(Alarm alarm : alarms) {
            alarm.setStatus(AlarmStatus.ACK);

            // (선택) 일괄 처리 시 남길 기본 코멘트가 있다면 세팅
            alarm.setComment("일괄 확인(ACK) 처리됨");
        }

        return alarms.size();
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
