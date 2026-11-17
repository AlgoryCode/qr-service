package com.ael.algoryqrservice.qrservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** A banner shown at the top of the member app for a time window. */
@Entity
@Table(name = "platform_announcement")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformAnnouncement {

    public enum Tone { INFO, SUCCESS, WARNING }

    public enum Audience { ALL, PAYING, FREE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 600)
    private String body;

    @Column(name = "link_url", length = 500)
    private String linkUrl;

    @Column(name = "link_label", length = 40)
    private String linkLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Tone tone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Audience audience;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    @Column(nullable = false)
    private boolean dismissible;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean liveAt(LocalDateTime now) {
        return active && !startsAt.isAfter(now) && (endsAt == null || endsAt.isAfter(now));
    }
}
