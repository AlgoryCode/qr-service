package com.ael.algoryqrservice.qrservice.repository;

import com.ael.algoryqrservice.qrservice.model.PlatformFeedback;
import com.ael.algoryqrservice.qrservice.model.enums.PlatformFeedbackStatus;
import org.springframework.data.jpa.domain.Specification;

public final class PlatformFeedbackSpecifications {

    private PlatformFeedbackSpecifications() {
    }

    public static Specification<PlatformFeedback> hasUserId(Long merchantId) {
        return (root, query, cb) -> merchantId == null ? cb.conjunction() : cb.equal(root.get("userId"), merchantId);
    }

    public static Specification<PlatformFeedback> hasStatus(PlatformFeedbackStatus status) {
        return (root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    public static Specification<PlatformFeedback> matchesQuery(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) {
                return cb.conjunction();
            }
            String pattern = "%" + q.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("subject")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)
            );
        };
    }
}
