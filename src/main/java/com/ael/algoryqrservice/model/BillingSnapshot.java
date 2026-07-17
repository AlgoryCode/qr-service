package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.BillingAddressType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingSnapshot {
    @Column(name = "billing_address_id")
    private Long billingAddressId;
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_type", length = 16)
    private BillingAddressType type;
    @Column(name = "billing_name")
    private String name;
    @Column(name = "billing_surname")
    private String surname;
    @Column(name = "billing_legal_name")
    private String legalName;
    @Column(name = "billing_tckn", length = 11)
    private String tckn;
    @Column(name = "billing_vkn", length = 10)
    private String vkn;
    @Column(name = "billing_tax_office")
    private String taxOffice;
    @Column(name = "billing_mersis")
    private String mersis;
    @Column(name = "billing_country")
    private String country;
    @Column(name = "billing_city")
    private String city;
    @Column(name = "billing_district")
    private String district;
    @Column(name = "billing_address", length = 1000)
    private String address;
    @Column(name = "billing_postcode", length = 16)
    private String postcode;
    @Column(name = "billing_email")
    private String email;
    @Column(name = "billing_phone", length = 32)
    private String phone;
    @Column(name = "billing_taxpayer_invoice")
    private Boolean taxpayerInvoice;
}
