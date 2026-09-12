package com.ael.algoryqrservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_user_theme_assignment", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_theme_assignment", columnNames = {"user_id", "theme_id"})
}, indexes = {
        @Index(name = "idx_user_theme_assignment_user_id", columnList = "user_id"),
        @Index(name = "idx_user_theme_assignment_theme_id", columnList = "theme_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class UserThemeAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /** Member account ({@code tbl_user.id}). */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "theme_id", nullable = false)
    private Long themeId;

    /** Optional dashboard admin ({@code tbl_dashboard_user.id}) who assigned the theme. */
    @Column(name = "assigned_by")
    private Long assignedBy;

    @CreationTimestamp
    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;
}
