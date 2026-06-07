package me.zonesafe.zonesafe_be.repository;

import me.zonesafe.zonesafe_be.domain.Roi;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoiRepository extends JpaRepository<Roi, Long> {
    List<Roi> findAllByCamera_CameraId(Long cameraId);
    boolean existsByCamera_CameraId(Long cameraId);
}