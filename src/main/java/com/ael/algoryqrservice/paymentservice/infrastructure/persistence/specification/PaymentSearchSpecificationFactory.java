package com.ael.algoryqrservice.paymentservice.infrastructure.persistence.specification;

import com.ael.algoryqrservice.paymentservice.domain.model.PaymentSearchQuery;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentStatus;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentStyle;
import com.ael.algoryqrservice.paymentservice.domain.model.PaymentType;
import com.ael.algoryqrservice.paymentservice.infrastructure.persistence.entity.PaymentEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class PaymentSearchSpecificationFactory {

    public Specification<PaymentEntity> create(PaymentSearchQuery query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            String freeText = trimToNull(query.query());
            if (freeText != null) {
                String pattern = "%" + freeText.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("conversationId")), pattern),
                        cb.like(cb.lower(root.get("paymentId")), pattern),
                        cb.like(cb.lower(root.get("paymentTransactionId")), pattern),
                        cb.like(cb.lower(root.get("accountId")), pattern),
                        cb.like(cb.lower(root.get("buyerEmail")), pattern),
                        cb.like(cb.lower(root.get("buyerName")), pattern),
                        cb.like(cb.lower(root.get("sourceReferenceId")), pattern)
                ));
            }

            PaymentStatus status = query.status();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            PaymentType paymentType = query.paymentType();
            if (paymentType != null) {
                predicates.add(cb.equal(root.get("paymentType"), paymentType));
            }

            PaymentStyle paymentStyle = query.paymentStyle();
            if (paymentStyle != null) {
                predicates.add(cb.equal(root.get("paymentStyle"), paymentStyle));
            }

            String accountId = trimToNull(query.accountId());
            if (accountId != null) {
                predicates.add(cb.equal(root.get("accountId"), accountId));
            }

            LocalDateTime createdFrom = query.createdFrom();
            if (createdFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
            }

            LocalDateTime createdTo = query.createdTo();
            if (createdTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), createdTo));
            }

            Boolean verificationOnly = query.verificationOnly();
            if (verificationOnly != null) {
                predicates.add(cb.equal(root.get("verificationOnly"), verificationOnly));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
