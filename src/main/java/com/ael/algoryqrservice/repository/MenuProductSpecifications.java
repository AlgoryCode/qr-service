package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.MenuSubCategory;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class MenuProductSpecifications {

    private MenuProductSpecifications() {
    }

    public static Specification<MenuProduct> forMenuSearch(
            Long menuId,
            boolean availableOnly,
            Boolean chefRecommended,
            BigDecimal minRating,
            Long tagId,
            Collection<Long> tagIds,
            Long allergenId,
            Collection<Long> allergenIds,
            Long subCategoryId,
            Long mainCategoryId,
            Integer servesPeople,
            Integer servesPeopleMin,
            Integer servesPeopleMax,
            String q
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("menuId"), menuId));
            predicates.add(cb.isFalse(root.get("deleted")));
            if (availableOnly) {
                predicates.add(cb.isTrue(root.get("available")));
            }
            if (chefRecommended != null) {
                predicates.add(cb.equal(root.get("chefRecommended"), chefRecommended));
            }
            if (minRating != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("ratingAvg"), minRating));
            }
            if (tagId != null) {
                predicates.add(cb.isMember(tagId, root.get("tagIds")));
            }
            if (tagIds != null) {
                for (Long id : tagIds) {
                    if (id != null) {
                        predicates.add(cb.isMember(id, root.get("tagIds")));
                    }
                }
            }
            if (allergenId != null) {
                predicates.add(cb.isMember(allergenId, root.get("allergenIds")));
            }
            if (allergenIds != null) {
                for (Long id : allergenIds) {
                    if (id != null) {
                        predicates.add(cb.isMember(id, root.get("allergenIds")));
                    }
                }
            }
            if (subCategoryId != null) {
                predicates.add(cb.equal(root.get("subCategoryId"), subCategoryId));
            }
            if (mainCategoryId != null) {
                Subquery<Long> subQuery = query.subquery(Long.class);
                Root<MenuSubCategory> subRoot = subQuery.from(MenuSubCategory.class);
                subQuery.select(subRoot.get("id"))
                        .where(
                                cb.equal(subRoot.get("menuCategoryId"), mainCategoryId),
                                cb.isFalse(subRoot.get("deleted"))
                        );
                predicates.add(root.get("subCategoryId").in(subQuery));
            }
            if (servesPeople != null) {
                predicates.add(cb.isNotNull(root.get("servesPeopleMin")));
                predicates.add(cb.isNotNull(root.get("servesPeopleMax")));
                predicates.add(cb.lessThanOrEqualTo(root.get("servesPeopleMin"), servesPeople));
                predicates.add(cb.greaterThanOrEqualTo(root.get("servesPeopleMax"), servesPeople));
            }
            if (servesPeopleMin != null || servesPeopleMax != null) {
                predicates.add(cb.isNotNull(root.get("servesPeopleMin")));
                predicates.add(cb.isNotNull(root.get("servesPeopleMax")));
                if (servesPeopleMax != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("servesPeopleMin"), servesPeopleMax));
                }
                if (servesPeopleMin != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("servesPeopleMax"), servesPeopleMin));
                }
            }
            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern)
                ));
            }
            if (query != null && query.getResultType() != null && query.getResultType() != Long.class) {
                query.orderBy(cb.asc(root.get("sortOrder")), cb.asc(root.get("productId")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
