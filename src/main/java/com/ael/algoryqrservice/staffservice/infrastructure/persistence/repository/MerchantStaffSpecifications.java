package com.ael.algoryqrservice.staffservice.infrastructure.persistence.repository;

import com.ael.algoryqrservice.qrservice.model.enums.StaffRole;
import com.ael.algoryqrservice.staffservice.infrastructure.persistence.entity.MerchantStaff;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class MerchantStaffSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private MerchantStaffSpecifications() {
    }

    /**
     * İşletme personel listesi için DB tarafı filtre.
     *
     * @param assigned   null: hepsi, true: şubeli, false: şubesiz; {@code branchIds} doluysa kullanılmaz
     * @param branchIds  bu şubelerden biri; {@code includeNoBranch} ile şubesizler de eklenir
     * @param staffRoles boşsa tüm roller
     */
    public static Specification<MerchantStaff> forMerchantSearch(
            Long merchantId,
            String query,
            Boolean assigned,
            Collection<Long> branchIds,
            boolean includeNoBranch,
            Boolean active,
            Collection<StaffRole> staffRoles
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

            boolean hasBranches = branchIds != null && !branchIds.isEmpty();
            if (hasBranches && includeNoBranch) {
                predicates.add(cb.or(root.get("branchId").in(branchIds), cb.isNull(root.get("branchId"))));
            } else if (hasBranches) {
                predicates.add(root.get("branchId").in(branchIds));
            } else if (includeNoBranch || Boolean.FALSE.equals(assigned)) {
                predicates.add(cb.isNull(root.get("branchId")));
            } else if (Boolean.TRUE.equals(assigned)) {
                predicates.add(cb.isNotNull(root.get("branchId")));
            }

            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }

            if (staffRoles != null && !staffRoles.isEmpty()) {
                predicates.add(root.get("staffRole").in(staffRoles));
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
