package me.zonesafe.zonesafe_be.service;

import lombok.RequiredArgsConstructor;
import me.zonesafe.zonesafe_be.dto.DetectionFrame;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DetectionEventPublisher {

    private static final String TOPIC_DETECTION_PREFIX = "/topic/detections/";

    private final SimpMessagingTemplate messagingTemplate;

    public void publishFrame(DetectionFrame frame) {
        if (frame.getCameraId() == null) return;
        messagingTemplate.convertAndSend(TOPIC_DETECTION_PREFIX + frame.getCameraId(), frame);
    }
}