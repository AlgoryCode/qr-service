package com.ael.algoryqrservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tbl_merchant_staff_session", indexes = {
        @Index(name = "idx_menu_waiter_session_waiter", columnList = "staff_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MerchantStaffSession {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "staff_id", nullable = false)
    private Long staffId;

    @Column(name = "refresh_token_hash", nullable = false)
    private String refreshTokenHash;

    @Column(name = "logged_in_at", nullable = false)
    private LocalDateTime loggedInAt;

    @Column(name = "access_expires_at", nullable = false)
    private LocalDateTime accessExpiresAt;

    @Column(name = "refresh_expires_at", nullable = false)
    private LocalDateTime refreshExpiresAt;

    @Column(name = "last_activity_at", nullable = false)
    private LocalDateTime lastActivityAt;

    @Column(nullable = false)
    private boolean revoked;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "device")
    private String device;

    @Column(name = "device_type")
    private String deviceType;

    public boolean isRefreshExpired() {
        return refreshExpiresAt.isBefore(LocalDateTime.now());
    }

    public boolean isActive() {
        return !revoked && !isRefreshExpired();
    }
}
