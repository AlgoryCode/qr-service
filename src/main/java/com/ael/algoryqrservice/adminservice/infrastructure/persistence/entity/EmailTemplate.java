package com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** An admin override of a built-in e-mail text. */
@Entity
@Table(name = "email_template")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailTemplate {

    @Id
    @Column(name = "template_key", length = 64)
    private String key;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "updated_by", length = 128)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
