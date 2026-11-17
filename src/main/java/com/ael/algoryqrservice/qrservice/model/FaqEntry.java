package com.ael.algoryqrservice.qrservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** One help-centre question and answer. */
@Entity
@Table(name = "faq_entry")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaqEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String category;

    @Column(nullable = false, length = 240)
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String answer;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean published;

    @Column(name = "updated_by", length = 128)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
