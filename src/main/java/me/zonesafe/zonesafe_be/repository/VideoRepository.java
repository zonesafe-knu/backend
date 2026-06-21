package me.zonesafe.zonesafe_be.repository;

import me.zonesafe.zonesafe_be.domain.Video;
import me.zonesafe.zonesafe_be.enums.VideoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;

public interface VideoRepository extends JpaRepository<Video, Long> {

    List<Video> findAllByCameraContext(Long cameraContext);

    @Query("SELECT v FROM Video v WHERE " +
            "(:status IS NULL OR v.status = :status) AND " +
            "(:siteId IS NULL OR v.siteId = :siteId) AND " +
            "(:cameraContext IS NULL OR v.cameraContext = :cameraContext) AND " +
            "(:uploadedBy IS NULL OR v.uploadedBy = :uploadedBy) AND " +
            "(:from IS NULL OR v.uploadedAt >= :from) AND " +
            "(:to IS NULL OR v.uploadedAt <= :to)")
    Page<Video> findAllByFilter(@Param("status") VideoStatus status,
                                @Param("siteId") Long siteId,
                                @Param("cameraContext") Long cameraContext,
                                @Param("uploadedBy") String uploadedBy,
                                @Param("from") ZonedDateTime from,
                                @Param("to") ZonedDateTime to,
                                Pageable pageable);
}
