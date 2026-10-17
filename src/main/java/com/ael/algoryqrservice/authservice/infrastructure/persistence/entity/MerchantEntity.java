package com.ael.algoryqrservice.authservice.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_merchant")
@Getter
@Setter
public class MerchantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tbl_merchant_id_seq")
    @SequenceGenerator(name = "tbl_merchant_id_seq", sequenceName = "tbl_merchant_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private RoleEntity role;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(nullable = false)
    private String email;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(unique = true)
    private String phone;

    @Column
    private String password;

    @Column(nullable = false, length = 32)
    private String provider = "BASIC";

    @Column(name = "provider_subject", unique = true, length = 128)
    private String providerSubject;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "email_verification_code_hash", length = 255)
    private String emailVerificationCodeHash;

    @Column(name = "email_verification_expires_at")
    private LocalDateTime emailVerificationExpiresAt;

    @Column(name = "email_verification_sent_at")
    private LocalDateTime emailVerificationSentAt;

    @Column(name = "email_verification_attempts", nullable = false)
    private int emailVerificationAttempts = 0;

    @Column(name = "email_verification_locked_until")
    private LocalDateTime emailVerificationLockedUntil;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
