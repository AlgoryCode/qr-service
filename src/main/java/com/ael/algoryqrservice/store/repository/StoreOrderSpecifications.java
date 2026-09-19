package com.ael.algoryqrservice.store.repository;

import com.ael.algoryqrservice.store.model.StoreOrder;
import com.ael.algoryqrservice.store.model.StoreOrderStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class StoreOrderSpecifications {

    private StoreOrderSpecifications() {
    }

    /**
     * Optional status/from/to filters cannot be expressed as {@code :param is null or ... in :param}
     * in Hibernate 6 — a null IN collection throws and 500s the merchant order list.
     */
    public static Specification<StoreOrder> search(
            Long merchantId,
            Collection<StoreOrderStatus> statuses,
            LocalDateTime from,
            LocalDateTime to
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("merchantId"), merchantId));
            if (statuses != null && !statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
