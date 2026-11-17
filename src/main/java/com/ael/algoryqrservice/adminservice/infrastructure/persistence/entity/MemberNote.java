package com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Internal note an admin wrote about a member; never shown to the member. */
@Entity
@Table(name = "member_note")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false)
    private boolean pinned;

    @Column(name = "author_id")
    private Long authorId;

    @Column(name = "author_name", length = 128)
    private String authorName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
