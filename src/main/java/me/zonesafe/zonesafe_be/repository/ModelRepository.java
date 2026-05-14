package me.zonesafe.zonesafe_be.repository;

import me.zonesafe.zonesafe_be.domain.Model;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ModelRepository extends JpaRepository<Model, Long> {
    Optional<Model> findByActiveTrue();

    @Modifying
    @Query("UPDATE Model m SET m.active = false WHERE m.active = true")
    int deactivateAll();
}