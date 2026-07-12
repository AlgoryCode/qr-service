package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.PurchaseLogAction;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_purchase_log", indexes = {
        @Index(name = "idx_purchase_log_user_id", columnList = "user_id"),
        @Index(name = "idx_purchase_log_purchase_id", columnList = "purchase_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purchase_id")
    private Long purchaseId;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PurchaseLogAction action;

    @Column(nullable = false, length = 1000)
    private String message;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
