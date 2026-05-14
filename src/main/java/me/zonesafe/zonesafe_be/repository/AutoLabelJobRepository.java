package me.zonesafe.zonesafe_be.repository;

import me.zonesafe.zonesafe_be.domain.AutoLabelJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutoLabelJobRepository extends JpaRepository<AutoLabelJob, String> {
}