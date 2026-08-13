package com.ael.algoryqrservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tbl_table_session", indexes = {
        @Index(name = "idx_table_session_table_id", columnList = "table_id"),
        @Index(name = "idx_table_session_menu_id", columnList = "menu_id"),
        @Index(name = "idx_table_session_expires_at", columnList = "expires_at")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = "session_token")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TableSession {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "table_id", nullable = false)
    private Long tableId;

    @Column(name = "menu_id", nullable = false)
    private Long menuId;

    @Column(name = "session_token", nullable = false, unique = true, length = 64)
    private String sessionToken;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }

    public boolean isActive() {
        return !revoked && !isExpired();
    }
}
