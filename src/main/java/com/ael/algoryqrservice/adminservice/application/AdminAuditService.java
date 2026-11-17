package com.ael.algoryqrservice.adminservice.application;

import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.AdminAuditLog;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.repository.AdminAuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Writes and searches the admin audit trail. Writing never breaks the request it describes. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuditService {

    private final AdminAuditLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AdminAuditLog entry) {
        try {
            repository.save(entry);
        } catch (RuntimeException e) {
            log.warn("admin_audit_write_failed action={} path={} reason={}", entry.getAction(), entry.getPath(), e.toString());
        }
    }

    @Transactional(readOnly = true)
    public Page<AdminAuditLog> search(String q, Long actorId, String outcome, String targetType, String targetId,
                                      LocalDateTime from, LocalDateTime to, int page, int size) {
        Specification<AdminAuditLog> spec = (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                all.add(cb.or(
                        cb.like(cb.lower(root.get("path")), like),
                        cb.like(cb.lower(root.get("action")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("actorName"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("requestBody"), "")), like)));
            }
            if (actorId != null) {
                all.add(cb.equal(root.get("actorId"), actorId));
            }
            if (outcome != null && !outcome.isBlank()) {
                all.add(cb.equal(root.get("outcome"), outcome.trim().toUpperCase(Locale.ROOT)));
            }
            if (targetType != null && !targetType.isBlank()) {
                all.add(cb.equal(root.get("targetType"), targetType.trim()));
            }
            if (targetId != null && !targetId.isBlank()) {
                all.add(cb.equal(root.get("targetId"), targetId.trim()));
            }
            if (from != null) {
                all.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                all.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(all.toArray(Predicate[]::new));
        };
        int pageSize = Math.min(Math.max(size, 1), 100);
        return repository.findAll(spec, PageRequest.of(Math.max(page, 0), pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }
}
