package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.UserAccountingEntry;
import com.ael.algoryqrservice.model.enums.AccountingEntryType;
import com.ael.algoryqrservice.model.enums.AccountingSourceType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class UserAccountingEntrySpecifications {

    private UserAccountingEntrySpecifications() {
    }

    public static Specification<UserAccountingEntry> forUser(
            Long userId,
            AccountingEntryType entryType,
            LocalDateTime from,
            LocalDateTime to,
            String pattern
    ) {
        return forUser(userId, entryType, from, to, pattern, false);
    }

    public static Specification<UserAccountingEntry> forUser(
            Long userId,
            AccountingEntryType entryType,
            LocalDateTime from,
            LocalDateTime to,
            String pattern,
            boolean excludeBillDerivedSources
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            if (excludeBillDerivedSources) {
                predicates.add(cb.not(root.get("sourceType").in(
                        AccountingSourceType.BILL_SALE,
                        AccountingSourceType.BILL_TIP
                )));
            }
            if (entryType != null) {
                predicates.add(cb.equal(root.get("entryType"), entryType));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to));
            }
            if (pattern != null) {
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("note"), cb.literal(""))), pattern)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
