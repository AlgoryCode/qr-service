package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.MenuReservation;
import com.ael.algoryqrservice.model.enums.MenuReservationStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class MenuReservationSpecifications {

    private MenuReservationSpecifications() {
    }

    public static Specification<MenuReservation> forOwner(
            Long menuId,
            MenuReservationStatus status,
            LocalDateTime from,
            LocalDateTime to,
            String pattern
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("menuId"), menuId));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("reservationAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("reservationAt"), to));
            }
            if (pattern != null) {
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("customerName")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("email"), cb.literal(""))), pattern)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
