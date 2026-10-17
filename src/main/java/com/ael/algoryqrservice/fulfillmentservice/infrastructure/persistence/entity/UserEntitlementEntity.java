package com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.entity;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.EntitlementSource;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.PackageStatus;
import com.ael.algoryqrservice.fulfillmentservice.domain.model.ProductType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "user_entitlement")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class UserEntitlementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "fulfillment_id", nullable = false)
    private Long fulfillmentId;

    @Column(name = "purchase_id")
    private Long purchaseId;

    @Column(name = "product_id")
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type_id", length = 32)
    private ProductType productTypeId;

    @Column(name = "feature_code", nullable = false, length = 64)
    private String featureCode;

    @Column(name = "scope_code", length = 64)
    private String scopeCode;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private boolean unlimited;

    @Column(name = "used_quantity", nullable = false)
    private Integer usedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EntitlementSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PackageStatus status;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
