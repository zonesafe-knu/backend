package me.zonesafe.zonesafe_be.controller;

import lombok.RequiredArgsConstructor;
import me.zonesafe.zonesafe_be.domain.Alarm;
import me.zonesafe.zonesafe_be.dto.AlarmStatusUpdateRequest;
import me.zonesafe.zonesafe_be.dto.PageResponseDto;
import me.zonesafe.zonesafe_be.dto.AlarmResponseDto;
import me.zonesafe.zonesafe_be.enums.AlarmSeverity;
import me.zonesafe.zonesafe_be.enums.AlarmStatus;
import me.zonesafe.zonesafe_be.enums.AlarmType;
import me.zonesafe.zonesafe_be.service.AlarmService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;

@RestController
@RequestMapping("/api/v1/alarms")
@RequiredArgsConstructor
public class AlarmController {
    private final AlarmService alarmService;


    //알람 목록 조회
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public PageResponseDto<AlarmResponseDto> getAlarms(
            @RequestParam(required = false) Long cameraId,
            @RequestParam(required = false) AlarmSeverity severity,
            @RequestParam(required = false) AlarmType type,
            @RequestParam(required = false) AlarmStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime to,
            @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<AlarmResponseDto> pageResult = alarmService.getAlarms(cameraId, severity, type, status, from, to, pageable);

        return new PageResponseDto<>(pageResult);
    }

    //알람 상세 정보
    @GetMapping("/{alarmId}")
    @ResponseStatus(HttpStatus.OK)
    public AlarmResponseDto getAlarmDetail(@PathVariable Long alarmId) {
        return alarmService.getAlarmById(alarmId);
    }

    //알람 확인,해제
    @PatchMapping("/{alarmId}/ack")
    @ResponseStatus(HttpStatus.OK)
    public AlarmResponseDto ackAlarm(
            @PathVariable Long alarmId,
            @RequestBody AlarmStatusUpdateRequest request
            ) {
        //상태를 ACK(확인)으로 변경
        return alarmService.updateAlarmStatus(alarmId, AlarmStatus.ACK, request.getComment());
    }

    @PatchMapping("/{alarmId}/resolve")
    @ResponseStatus(HttpStatus.OK)
    public AlarmResponseDto resolveAlarm(
            @PathVariable Long alarmId,
            @RequestBody AlarmStatusUpdateRequest request
    ) {
        //상태를 RESOLVE(해제)으로 변경
        return alarmService.updateAlarmStatus(alarmId, AlarmStatus.RESOLVED, request.getComment());
    }
}
