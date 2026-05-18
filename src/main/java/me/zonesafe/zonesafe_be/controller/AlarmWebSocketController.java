package me.zonesafe.zonesafe_be.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.zonesafe.zonesafe_be.dto.AlarmAckMessage;
import me.zonesafe.zonesafe_be.enums.AlarmStatus;
import me.zonesafe.zonesafe_be.service.AlarmService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AlarmWebSocketController {

    private final AlarmService alarmService;

    @MessageMapping("/ack")
    public void ackAlarm(AlarmAckMessage message) {
        if (message == null || message.getAlarmId() == null) {
            log.warn("Received /app/ack with empty payload");
            return;
        }
        try {
            alarmService.updateAlarmStatus(message.getAlarmId(), AlarmStatus.ACK, "WebSocket ACK");
        } catch (RuntimeException e) {
            log.warn("Failed to ACK alarm via WebSocket. alarmId={}, reason={}",
                    message.getAlarmId(), e.getMessage());
        }
    }
}