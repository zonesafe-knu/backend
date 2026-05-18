package me.zonesafe.zonesafe_be.controller;

import lombok.RequiredArgsConstructor;
import me.zonesafe.zonesafe_be.dto.AlarmCreateRequest;
import me.zonesafe.zonesafe_be.dto.AlarmResponseDto;
import me.zonesafe.zonesafe_be.service.AlarmService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/alarms")
@RequiredArgsConstructor
public class InternalAlarmController {

    private final AlarmService alarmService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AlarmResponseDto createAlarm(@RequestBody AlarmCreateRequest request) {
        return alarmService.createAlarm(request);
    }
}