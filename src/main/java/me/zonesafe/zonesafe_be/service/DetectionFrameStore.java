package me.zonesafe.zonesafe_be.service;

import me.zonesafe.zonesafe_be.dto.DetectionFrame;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DetectionFrameStore {

    // videoId → (videoTimeSec → frame)
    private final Map<Long, Map<Double, DetectionFrame>> store = new ConcurrentHashMap<>();

    public void put(Long videoId, DetectionFrame frame) {
        if (videoId == null || frame.getVideoTimeSec() == null) return;
        store.computeIfAbsent(videoId, id -> new ConcurrentHashMap<>())
             .put(frame.getVideoTimeSec(), frame);
    }

    public List<DetectionFrame> getAll(Long videoId) {
        Map<Double, DetectionFrame> frames = store.get(videoId);
        if (frames == null) return List.of();
        return frames.values().stream()
                .sorted(Comparator.comparingDouble(DetectionFrame::getVideoTimeSec))
                .toList();
    }

    public void clear(Long videoId) {
        store.remove(videoId);
    }
}
