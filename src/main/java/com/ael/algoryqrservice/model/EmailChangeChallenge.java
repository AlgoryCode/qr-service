package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.EmailChangeChallengeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "tbl_email_change_challenge",
        indexes = {
                @Index(name = "idx_email_change_challenge_user", columnList = "user_id"),
                @Index(name = "idx_email_change_challenge_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailChangeChallenge {

    @Id
    @Column(nullable = false, length = 36)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "current_email", nullable = false, length = 320)
    private String currentEmail;

    @Column(name = "new_email", length = 320)
    private String newEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EmailChangeChallengeStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "current_verified_at")
    private LocalDateTime currentVerifiedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = EmailChangeChallengeStatus.AWAITING_CURRENT_CODE;
        }
    }
}
