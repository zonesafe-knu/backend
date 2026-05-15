package me.zonesafe.zonesafe_be.repository;

import me.zonesafe.zonesafe_be.domain.VideoAnalysisJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoAnalysisJobRepository extends JpaRepository<VideoAnalysisJob, String> {
}
