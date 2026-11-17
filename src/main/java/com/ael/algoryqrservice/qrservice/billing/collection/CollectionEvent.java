package com.ael.algoryqrservice.qrservice.billing.collection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** A status change or note on a collection case; never edited, so it doubles as the audit trail. */
@Entity
@Table(name = "billing_collection_event")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionEvent {

    public static final String SYSTEM_ACTOR = "system";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 32)
    private CollectionStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private CollectionStatus toStatus;

    @Column(length = 2000)
    private String note;

    @Column(nullable = false)
    private String actor;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
