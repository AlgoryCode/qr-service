package com.ael.algoryqrservice.qrservice.model;

import com.ael.algoryqrservice.qrservice.model.enums.PlatformFeedbackStatus;
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

@Entity
@Table(name = "tbl_platform_feedback", indexes = {
        @Index(name = "idx_platform_feedback_user_created", columnList = "merchant_id, created_at"),
        @Index(name = "idx_platform_feedback_status_created", columnList = "status, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PlatformFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 60)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "screenshot_url", columnDefinition = "text")
    private String screenshotUrl;

    @Column(name = "screenshot_key", columnDefinition = "text")
    private String screenshotKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PlatformFeedbackStatus status = PlatformFeedbackStatus.OPEN;

    @Column(name = "admin_note", columnDefinition = "text")
    private String adminNote;

    /** LOW, NORMAL, HIGH or URGENT. */
    @Column(nullable = false, length = 16)
    @Builder.Default
    private String priority = "NORMAL";

    @Column(name = "assignee_id")
    private Long assigneeId;

    @Column(name = "assignee_name", length = 128)
    private String assigneeName;

    @Column(name = "first_response_at")
    private LocalDateTime firstResponseAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
