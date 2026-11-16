package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.MenuReservation;
import com.ael.algoryqrservice.qrservice.model.enums.MenuReservationStatus;
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
        return filtered("menuId", menuId, status, from, to, pattern);
    }

    /** Every reservation of a branch, whichever menu (or the panel) it came from. */
    public static Specification<MenuReservation> forBranch(
            Long branchId,
            MenuReservationStatus status,
            LocalDateTime from,
            LocalDateTime to,
            String pattern
    ) {
        return filtered("branchId", branchId, status, from, to, pattern);
    }

    private static Specification<MenuReservation> filtered(
            String scopeField,
            Long scopeId,
            MenuReservationStatus status,
            LocalDateTime from,
            LocalDateTime to,
            String pattern
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get(scopeField), scopeId));
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
                        cb.like(cb.lower(cb.coalesce(root.get("email"), cb.literal(""))), pattern),
                        cb.like(cb.coalesce(root.get("phone"), cb.literal("")), pattern)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
