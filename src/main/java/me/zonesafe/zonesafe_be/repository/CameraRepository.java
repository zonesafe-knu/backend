package me.zonesafe.zonesafe_be.repository;

import me.zonesafe.zonesafe_be.domain.Camera;
import me.zonesafe.zonesafe_be.enums.CameraStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CameraRepository extends JpaRepository<Camera, Long> {
    List<Camera> findAllBySiteIdAndStatus(Long siteId, CameraStatus status);
    List<Camera> findAllBySiteId(Long siteId);
    List<Camera> findAllByStatus(CameraStatus status);
}
