package me.zonesafe.zonesafe_be.domain;

import me.zonesafe.zonesafe_be.enums.AlarmSeverity;
import me.zonesafe.zonesafe_be.enums.AlarmStatus;
import me.zonesafe.zonesafe_be.enums.AlarmType;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class AlarmSpecification {
    public static Specification<Alarm> filterAlarms(
            Long cameraId, Long roiId, AlarmSeverity severity,
            AlarmType type, AlarmStatus status,
            ZonedDateTime from, ZonedDateTime to) {

        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (cameraId != null) {
                predicates.add(criteriaBuilder.equal(root.get("camera").get("cameraId"), cameraId));
            }
            if (roiId != null) predicates.add(criteriaBuilder.equal(root.get("roiId"), roiId));
            if (severity != null) predicates.add(criteriaBuilder.equal(root.get("severity"), severity));
            if (type != null) predicates.add(criteriaBuilder.equal(root.get("type"), type));
            if (status != null) predicates.add(criteriaBuilder.equal(root.get("status"), status));
            if (from != null) predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("occurredAt"), from));
            if (to != null) predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("occurredAt"), to));

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
