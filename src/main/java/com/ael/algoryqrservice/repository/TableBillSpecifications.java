package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.TableBill;
import com.ael.algoryqrservice.model.enums.TableBillStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class TableBillSpecifications {

    private TableBillSpecifications() {
    }

    public static Specification<TableBill> closedForMenus(
            Collection<Long> menuIds,
            TableBillStatus status,
            LocalDateTime from,
            LocalDateTime to
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("menuId").in(menuIds));
            predicates.add(cb.equal(root.get("status"), status));
            predicates.add(cb.isNotNull(root.get("closedAt")));
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("closedAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("closedAt"), to));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
