package com.ael.algoryqrservice.staffservice.infrastructure.persistence.repository;

import com.ael.algoryqrservice.qrservice.model.enums.StaffRole;
import com.ael.algoryqrservice.staffservice.infrastructure.persistence.entity.MerchantStaff;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MerchantStaffSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private MerchantStaffSpecifications() {
    }

    /**
     * İşletme personel listesi için DB tarafı filtre.
     *
     * @param assigned null: hepsi, true: şubeli, false: şubesiz
     * @param branchId yalnızca şubeli personelde belirli şube
     */
    public static Specification<MerchantStaff> forMerchantSearch(
            Long merchantId,
            String query,
            Boolean assigned,
            Long branchId,
            Boolean active,
            StaffRole staffRole
    ) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("merchantId"), merchantId));

            String trimmed = query == null ? "" : query.trim();
            if (!trimmed.isEmpty()) {
                String pattern = "%" + escapeLike(trimmed.toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("displayName")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("username")), pattern, LIKE_ESCAPE)
                ));
            }

            if (branchId != null && branchId > 0) {
                predicates.add(cb.equal(root.get("branchId"), branchId));
            } else if (Boolean.TRUE.equals(assigned)) {
                predicates.add(cb.isNotNull(root.get("branchId")));
            } else if (Boolean.FALSE.equals(assigned)) {
                predicates.add(cb.isNull(root.get("branchId")));
            }

            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }

            if (staffRole != null) {
                predicates.add(cb.equal(root.get("staffRole"), staffRole));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
