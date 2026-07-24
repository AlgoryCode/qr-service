package com.ael.algoryqrservice.model;

import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.model.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_user", uniqueConstraints = {
        @UniqueConstraint(columnNames = "email"),
        @UniqueConstraint(columnNames = "phone"),
        @UniqueConstraint(columnNames = "provider_subject")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(unique = true)
    private String phone;

    @Column
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 16)
    @ColumnDefault("'BASIC'")
    @Builder.Default
    private AuthProvider provider = AuthProvider.BASIC;

    @Column(name = "provider_subject", unique = true, length = 128)
    private String providerSubject;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    @ColumnDefault("'USER'")
    @Builder.Default
    private UserRole role = UserRole.USER;

    @Column(name = "registration_ip_address")
    private String registrationIpAddress;

    @Column(name = "registration_user_agent", length = 512)
    private String registrationUserAgent;

    @Column(name = "registration_device")
    private String registrationDevice;

    @Column(name = "registration_device_type")
    private String registrationDeviceType;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column(name = "two_factor_enabled", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private boolean twoFactorEnabled = false;

    @Column(name = "notify_email_important", nullable = false)
    @ColumnDefault("true")
    @Builder.Default
    private boolean notifyEmailImportant = true;

    @Column(name = "notify_scan_alerts", nullable = false)
    @ColumnDefault("true")
    @Builder.Default
    private boolean notifyScanAlerts = true;

    @Column(name = "notify_weekly_report", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private boolean notifyWeeklyReport = false;

    @Column(name = "notify_marketing_emails", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private boolean notifyMarketingEmails = false;

    @Column(name = "notify_push_browser", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private boolean notifyPushBrowser = false;

    @Column(name = "trial_used", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private boolean trialUsed = false;
}
