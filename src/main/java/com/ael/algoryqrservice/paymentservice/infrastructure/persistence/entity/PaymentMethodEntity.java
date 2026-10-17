package com.ael.algoryqrservice.paymentservice.infrastructure.persistence.entity;

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

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tbl_payment_method")
public class PaymentMethodEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_reference_id", nullable = false, length = 64)
    private String ownerReferenceId;

    @Column(name = "account_id", nullable = false, length = 64)
    private String accountId;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "card_user_key", nullable = false, length = 255)
    private String cardUserKey;

    @Column(name = "card_token", nullable = false, length = 255)
    private String cardToken;

    @Column(name = "card_alias", length = 255)
    private String cardAlias;

    @Column(name = "last_four_digits", length = 4)
    private String lastFourDigits;

    @Column(name = "card_association", length = 64)
    private String cardAssociation;

    @Column(name = "card_family", length = 64)
    private String cardFamily;

    @Column(name = "bank_name", length = 128)
    private String bankName;

    @Column(name = "is_default", nullable = false)
    private boolean defaultMethod;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
