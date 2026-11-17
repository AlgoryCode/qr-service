package com.ael.algoryqrservice.qrservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** A reply or internal note on a support ticket. */
@Entity
@Table(name = "platform_feedback_message")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformFeedbackMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "feedback_id", nullable = false)
    private Long feedbackId;

    @Column(name = "author_name", length = 128)
    private String authorName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    /** Internal notes are never shown or sent to the member. */
    @Column(nullable = false)
    private boolean internal;

    @Column(nullable = false)
    private boolean emailed;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
