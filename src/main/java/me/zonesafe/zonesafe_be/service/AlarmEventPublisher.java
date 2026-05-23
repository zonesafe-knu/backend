package me.zonesafe.zonesafe_be.service;

import lombok.RequiredArgsConstructor;
import me.zonesafe.zonesafe_be.dto.AlarmEvent;
import me.zonesafe.zonesafe_be.dto.AlarmStatusChangedEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AlarmEventPublisher {

    private static final String TOPIC_ALL = "/topic/alarms";
    private static final String TOPIC_CAMERA_PREFIX = "/topic/alarms/camera/";
    private static final String TOPIC_STATUS = "/topic/alarms/status";

    private final SimpMessagingTemplate messagingTemplate;

    public void publish(AlarmEvent event) {
        messagingTemplate.convertAndSend(TOPIC_ALL, event);
        if (event.getCameraId() != null) {
            messagingTemplate.convertAndSend(TOPIC_CAMERA_PREFIX + event.getCameraId(), event);
        }
    }

    public void publishStatusChange(AlarmStatusChangedEvent event) {
        messagingTemplate.convertAndSend(TOPIC_STATUS, event);
    }
}