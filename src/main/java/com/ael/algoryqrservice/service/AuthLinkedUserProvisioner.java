package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.enums.AuthProvider;
import com.ael.algoryqrservice.model.enums.UserRole;
import com.ael.algoryqrservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthLinkedUserProvisioner {

    private static final Logger log = LoggerFactory.getLogger(AuthLinkedUserProvisioner.class);

    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public User ensureUser(Long userId, String email, String displayName) {
        return userRepository.findById(userId).orElseGet(() -> {
            String firstName = displayName == null || displayName.isBlank() ? "Demo" : displayName.trim();
            String normalizedEmail = email.trim().toLowerCase();
            try {
                jdbcTemplate.update(
                        """
                        INSERT INTO tbl_user (
                            id, first_name, email, provider, role, email_verified,
                            two_factor_enabled, notify_email_important, notify_scan_alerts,
                            notify_weekly_report, notify_marketing_emails, notify_push_browser,
                            trial_used, email_verification_attempts, created_at, updated_at
                        ) OVERRIDING SYSTEM VALUE VALUES (
                            ?, ?, ?, 'BASIC', 'USER', TRUE,
                            FALSE, TRUE, TRUE,
                            FALSE, FALSE, FALSE,
                            FALSE, 0, NOW(), NOW()
                        )
                        """,
                        userId,
                        firstName,
                        normalizedEmail
                );
                syncUserIdSequence();
            } catch (DataIntegrityViolationException exception) {
                log.debug("Auth-linked user insert race userId={} reason={}", userId, exception.getMessage());
            }
            return userRepository.findById(userId).orElseThrow(
                    () -> new IllegalStateException("Auth-linked user could not be provisioned for id=" + userId)
            );
        });
    }

    private void syncUserIdSequence() {
        jdbcTemplate.queryForObject(
                """
                SELECT setval(
                    pg_get_serial_sequence('tbl_user', 'id'),
                    (SELECT COALESCE(MAX(id), 1) FROM tbl_user)
                )
                """,
                Long.class
        );
    }

    @Transactional
    public User refreshDemoProfile(User user, String displayName) {
        if (displayName != null && !displayName.isBlank()) {
            user.setFirstName(displayName.trim());
        }
        user.setEmailVerified(true);
        user.setProvider(AuthProvider.BASIC);
        user.setRole(UserRole.USER);
        return userRepository.save(user);
    }
}
