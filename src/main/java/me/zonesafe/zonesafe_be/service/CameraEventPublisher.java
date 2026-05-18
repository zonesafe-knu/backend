package me.zonesafe.zonesafe_be.service;

import lombok.RequiredArgsConstructor;
import me.zonesafe.zonesafe_be.dto.CameraStatusEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CameraEventPublisher {

    private static final String TOPIC_CAMERA_STATUS_PREFIX = "/topic/cameras/";
    private static final String TOPIC_CAMERA_STATUS_SUFFIX = "/status";

    private final SimpMessagingTemplate messagingTemplate;

    public void publishStatus(CameraStatusEvent event) {
        if (event.getCameraId() == null) return;
        String destination = TOPIC_CAMERA_STATUS_PREFIX + event.getCameraId() + TOPIC_CAMERA_STATUS_SUFFIX;
        messagingTemplate.convertAndSend(destination, event);
    }
}