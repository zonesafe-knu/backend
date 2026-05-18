package me.zonesafe.zonesafe_be.repository;

import me.zonesafe.zonesafe_be.domain.VideoAnalysisEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VideoAnalysisEventRepository extends JpaRepository<VideoAnalysisEvent, Long> {
    List<VideoAnalysisEvent> findAllByVideo_VideoIdOrderByFrameTimestampAsc(Long videoId);
}
