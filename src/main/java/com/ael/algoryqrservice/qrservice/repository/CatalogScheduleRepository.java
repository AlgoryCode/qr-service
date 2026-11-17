package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.CatalogSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface CatalogScheduleRepository extends JpaRepository<CatalogSchedule, Long> {

    List<CatalogSchedule> findAllByOrderByStartsAtDesc();

    List<CatalogSchedule> findByTargetTypeAndTargetIdAndStatusIn(
            CatalogSchedule.TargetType targetType, Long targetId, Collection<CatalogSchedule.Status> statuses);

    List<CatalogSchedule> findByStatusAndStartsAtLessThanEqualOrderByStartsAtAsc(
            CatalogSchedule.Status status, LocalDateTime now);

    List<CatalogSchedule> findByStatusAndEndsAtLessThanEqualOrderByEndsAtAsc(
            CatalogSchedule.Status status, LocalDateTime now);

    List<CatalogSchedule> findByTargetTypeAndStatus(CatalogSchedule.TargetType targetType, CatalogSchedule.Status status);
}
