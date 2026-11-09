package com.ael.algoryqrservice.qrservice.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/** İşletmenin sipariş akışı: kanal başına hangi adımların yer aldığı. Şubeler bir akışa atanır. */
@Entity
@Table(name = "tbl_order_flow")
@Data
@EqualsAndHashCode(callSuper = false, exclude = "steps")
@ToString(exclude = "steps")
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class OrderFlow extends QrBaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(nullable = false, length = 120)
    private String name;

    /** Akışın başladığı hazır şablon; yalnızca bilgi amaçlı, adımlar sonradan değişebilir. */
    @Column(name = "template_code", length = 40)
    private String templateCode;

    /** Her kayıtta artar; personel uygulamaları değişikliği buradan anlar. */
    @Column(nullable = false)
    @Builder.Default
    private int version = 1;

    @OneToMany(mappedBy = "flow", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("channel ASC, sortOrder ASC")
    @Builder.Default
    private List<OrderFlowStep> steps = new ArrayList<>();
}
