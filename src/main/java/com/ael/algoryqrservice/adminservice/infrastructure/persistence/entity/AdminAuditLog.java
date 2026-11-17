package com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** One change attempted through the admin panel (allowed, refused or failed); never edited. */
@Entity
@Table(name = "admin_audit_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_name")
    private String actorName;

    @Column(name = "actor_role", length = 32)
    private String actorRole;

    @Column(nullable = false, length = 10)
    private String method;

    @Column(nullable = false, length = 512)
    private String path;

    @Column(nullable = false, length = 128)
    private String action;

    @Column(name = "target_type", length = 64)
    private String targetType;

    @Column(name = "target_id", length = 64)
    private String targetId;

    @Column(nullable = false)
    private int status;

    /** SUCCESS, DENIED or FAILED. */
    @Column(nullable = false, length = 16)
    private String outcome;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "request_body", columnDefinition = "TEXT")
    private String requestBody;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
