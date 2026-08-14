package com.ael.algoryqrservice.repository;

import com.ael.algoryqrservice.model.User;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> forAdminSearch(String query) {
        String trimmed = query.trim().toLowerCase();
        String pattern = "%" + trimmed + "%";

        return (root, q, cb) -> {
            List<Predicate> orPredicates = new ArrayList<>();
            orPredicates.add(cb.like(cb.lower(root.get("email")), pattern));
            orPredicates.add(cb.like(cb.lower(root.get("firstName")), pattern));
            orPredicates.add(cb.like(
                    cb.lower(cb.coalesce(root.get("lastName"), cb.literal(""))),
                    pattern
            ));
            orPredicates.add(cb.like(
                    cb.lower(cb.concat(
                            cb.concat(root.get("firstName"), cb.literal(" ")),
                            cb.coalesce(root.get("lastName"), cb.literal(""))
                    )),
                    pattern
            ));

            if (trimmed.matches("\\d+")) {
                orPredicates.add(cb.equal(root.get("id"), Long.parseLong(trimmed)));
            }

            return cb.or(orPredicates.toArray(Predicate[]::new));
        };
    }
}
