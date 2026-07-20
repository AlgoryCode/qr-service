package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.MenuAnalyticsEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tbl_menu_analytics_event", indexes = {
        @Index(name = "idx_menu_analytics_event_menu_occurred", columnList = "menu_id,occurred_at"),
        @Index(name = "idx_menu_analytics_event_session_seq", columnList = "session_id,sequence"),
        @Index(name = "idx_menu_analytics_event_menu_type_product", columnList = "menu_id,event_type,product_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MenuAnalyticsEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "menu_id", nullable = false)
    private Long menuId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private MenuAnalyticsEventType eventType;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "product_id")
    private Long productId;

    @Column(nullable = false)
    private int sequence;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
}
