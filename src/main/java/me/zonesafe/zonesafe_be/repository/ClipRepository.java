package me.zonesafe.zonesafe_be.repository;

import me.zonesafe.zonesafe_be.domain.Clip;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;

public interface ClipRepository extends JpaRepository<Clip, Long> {
    @Query("SELECT c FROM Clip c WHERE " +
            "(:cameraId IS NULL OR c.camera.cameraId = :cameraId) AND " +
            "(:from IS NULL OR c.occurredAt >= :from) AND " +
            "(:to IS NULL OR c.occurredAt <= :to)")
    Page<Clip> findAllByFilter(@Param("cameraId") Long cameraId,
                               @Param("from") ZonedDateTime from,
                               @Param("to") ZonedDateTime to,
                               Pageable pageable);
}
