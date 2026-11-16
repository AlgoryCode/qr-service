package com.ael.algoryqrservice.qrservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** A merchant's answers about one smart report; list columns hold JSON arrays of strings. */
@Entity
@Table(
        name = "tbl_smart_report_feedback",
        uniqueConstraints = @UniqueConstraint(name = "uk_smart_report_feedback_process", columnNames = "process_id"),
        indexes = @Index(name = "idx_smart_report_feedback_merchant", columnList = "merchant_id, created_at")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmartReportFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "process_id", nullable = false)
    private UUID processId;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "helpful", length = 16)
    private String helpful;

    @Column(name = "recommendations_fit", length = 16)
    private String recommendationsFit;

    @Column(name = "detail_level", length = 16)
    private String detailLevel;

    @Column(name = "unclear_sections", columnDefinition = "TEXT")
    private String unclearSections;

    @Column(name = "focus_areas", columnDefinition = "TEXT")
    private String focusAreas;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "revision_process_id")
    private UUID revisionProcessId;

    /** Monday of the week this feedback earned the weekly bonus credit in; null when it earned none. */
    @Column(name = "bonus_week")
    private LocalDate bonusWeek;

    /** When a smart report spent the bonus credit; null while it is still available. */
    @Column(name = "bonus_used_at")
    private LocalDateTime bonusUsedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
