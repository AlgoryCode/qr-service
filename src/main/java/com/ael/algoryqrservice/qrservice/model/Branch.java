package com.ael.algoryqrservice.qrservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "tbl_branch")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Branch extends QrBaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 1000)
    private String address;

    @Column(length = 64)
    private String phone;

    @Column(length = 255)
    private String email;

    @Column(name = "photo_url", length = 1024)
    private String photoUrl;

    @Column(name = "photo_key", length = 255)
    private String photoKey;

    @Column(nullable = false)
    @Builder.Default
    private boolean grandfathered = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "kitchen_enabled", nullable = false)
    @Builder.Default
    private boolean kitchenEnabled = false;

    @Column(name = "courier_enabled", nullable = false)
    @Builder.Default
    private boolean courierEnabled = false;

    @Column(name = "print_kitchen_enabled", nullable = false)
    @Builder.Default
    private boolean printKitchenEnabled = false;

    /** Şubenin sipariş akışı; boşsa eski davranış ({@link #kitchenEnabled}, {@link #courierEnabled}) geçerlidir. */
    @Column(name = "order_flow_id")
    private Long orderFlowId;

    /** Receipt heading; printers fall back to the branch name when it is empty. */
    @Column(name = "receipt_header", length = 120)
    private String receiptHeader;

    /** Paragraph printed under the heading, e.g. opening hours or a welcome line. */
    @Column(name = "receipt_intro", length = 500)
    private String receiptIntro;

    /** Closing lines after the order details, e.g. a thank-you note. */
    @Column(name = "receipt_footer", length = 500)
    private String receiptFooter;
}
