package com.ael.algoryqrservice.integration.yemeksepeti.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "yemeksepeti_connections", indexes = {
        @Index(name = "uk_yemeksepeti_connections_user", columnList = "user_id", unique = true),
        @Index(name = "idx_yemeksepeti_connections_vendor", columnList = "vendor_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class YemekSepetiConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "chain_id", nullable = false, length = 128)
    private String chainId;

    @Column(name = "vendor_id", length = 128)
    private String vendorId;

    @Column(name = "vendor_name", length = 255)
    private String vendorName;

    @Column(name = "client_id_encrypted", nullable = false, columnDefinition = "text")
    private String clientIdEncrypted;

    @Column(name = "client_secret_encrypted", nullable = false, columnDefinition = "text")
    private String clientSecretEncrypted;

    @Column(name = "webhook_secret_encrypted", nullable = false, columnDefinition = "text")
    private String webhookSecretEncrypted;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private YemekSepetiConnectionStatus status;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = YemekSepetiConnectionStatus.DISCONNECTED;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
