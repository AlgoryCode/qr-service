package com.ael.algoryqrservice.integration.trendyolgo.model;

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
@Table(name = "tbl_trendyol_go_connection", indexes = {
        @Index(name = "idx_tgo_connection_user_branch", columnList = "user_id, branch_id", unique = true),
        @Index(name = "idx_tgo_connection_restaurant", columnList = "restaurant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class TrendyolGoConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "seller_id", nullable = false, length = 64)
    private String sellerId;

    @Column(name = "api_key_encrypted", nullable = false, columnDefinition = "text")
    private String apiKeyEncrypted;

    @Column(name = "api_secret_encrypted", nullable = false, columnDefinition = "text")
    private String apiSecretEncrypted;

    @Column(name = "restaurant_id", length = 64)
    private String restaurantId;

    @Column(name = "restaurant_name", length = 255)
    private String restaurantName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TrendyolGoConnectionStatus status;

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
            status = TrendyolGoConnectionStatus.DISCONNECTED;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
