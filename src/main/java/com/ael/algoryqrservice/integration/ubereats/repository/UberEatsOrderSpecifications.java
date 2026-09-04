package com.ael.algoryqrservice.integration.ubereats.repository;

import com.ael.algoryqrservice.integration.ubereats.model.UberEatsOrder;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class UberEatsOrderSpecifications {

    private UberEatsOrderSpecifications() {
    }

    public static Specification<UberEatsOrder> forConnectionListed(
            Long connectionId,
            String packageStatus,
            LocalDateTime from,
            LocalDateTime to
    ) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("connectionId"), connectionId));
            if (packageStatus != null && !packageStatus.isBlank()) {
                predicates.add(cb.equal(
                        cb.lower(root.get("packageStatus")),
                        packageStatus.trim().toLowerCase(Locale.ROOT)
                ));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("packageCreatedAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("packageCreatedAt"), to));
            }
            if (query != null) {
                query.orderBy(cb.desc(root.get("packageCreatedAt")));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
