package me.zonesafe.zonesafe_be.controller;

import lombok.RequiredArgsConstructor;
import me.zonesafe.zonesafe_be.dto.DetectionFrame;
import me.zonesafe.zonesafe_be.service.DetectionEventPublisher;
import me.zonesafe.zonesafe_be.service.DetectionFrameStore;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;

@RestController
@RequestMapping("/api/v1/internal/detections")
@RequiredArgsConstructor
public class InternalDetectionController {

    private final DetectionEventPublisher detectionEventPublisher;
    private final DetectionFrameStore detectionFrameStore;

    @PostMapping("/frame")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void publishDetectionFrame(@RequestBody DetectionFrame frame) {
        if (frame.getFrameTs() == null) {
            frame.setFrameTs(ZonedDateTime.now());
        }
        detectionEventPublisher.publishFrame(frame);
        if (frame.getVideoId() != null) {
            detectionFrameStore.put(frame.getVideoId(), frame);
        }
    }
}