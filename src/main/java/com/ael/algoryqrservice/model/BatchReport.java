package com.ael.algoryqrservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tbl_batch_reports", indexes = {
        @Index(name = "idx_batch_reports_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_batch_reports_status_updated", columnList = "status, updated_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchReport {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_VALIDATING = "validating";
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_FINALIZING = "finalizing";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_EXPIRED = "expired";
    public static final String STATUS_CANCELLING = "cancelling";
    public static final String STATUS_CANCELLED = "cancelled";

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "openai_batch_id", nullable = false, length = 128, unique = true)
    private String openaiBatchId;

    @Column(nullable = false, length = 32)
    @Builder.Default
    private String status = STATUS_PENDING;

    @Column(name = "request_total", nullable = false)
    @Builder.Default
    private Integer requestTotal = 0;

    @Column(name = "request_completed", nullable = false)
    @Builder.Default
    private Integer requestCompleted = 0;

    @Column(name = "request_failed", nullable = false)
    @Builder.Default
    private Integer requestFailed = 0;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
