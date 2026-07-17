package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.BillingAddressType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_billing_address", indexes = {
        @Index(name = "idx_billing_address_user", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingAddress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BillingAddressType type;
    private String name;
    private String surname;
    private String legalName;
    @Column(length = 11)
    private String tckn;
    @Column(length = 10)
    private String vkn;
    private String taxOffice;
    private String mersis;
    @Column(nullable = false)
    private String country;
    @Column(nullable = false)
    private String city;
    @Column(nullable = false)
    private String district;
    @Column(nullable = false, length = 1000)
    private String address;
    @Column(nullable = false, length = 16)
    private String postcode;
    @Column(nullable = false)
    private String email;
    @Column(nullable = false, length = 32)
    private String phone;
    @Column(name = "taxpayer_invoice", nullable = false)
    private boolean taxpayerInvoice;
    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
