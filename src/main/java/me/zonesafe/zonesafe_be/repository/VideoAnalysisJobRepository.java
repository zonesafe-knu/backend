package me.zonesafe.zonesafe_be.repository;

import me.zonesafe.zonesafe_be.domain.VideoAnalysisJob;
import me.zonesafe.zonesafe_be.enums.AnalysisJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface VideoAnalysisJobRepository extends JpaRepository<VideoAnalysisJob, String> {
    Optional<VideoAnalysisJob> findFirstByVideo_VideoIdAndStatusInOrderByCreatedAtDesc(
            Long videoId, Collection<AnalysisJobStatus> statuses);
}
