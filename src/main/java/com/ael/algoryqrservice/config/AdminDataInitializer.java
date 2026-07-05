package com.ael.algoryqrservice.config;

import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.enums.UserRole;
import com.ael.algoryqrservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("stage")
@RequiredArgsConstructor
@Slf4j
public class AdminDataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.admin.email:admin@algorycode.com}")
    private String email;

    @Value("${app.seed.admin.password:Admin123!}")
    private String password;

    @Value("${app.seed.admin.phone:+905550000001}")
    private String phone;

    @Value("${app.seed.admin.first-name:Admin}")
    private String firstName;

    @Value("${app.seed.admin.last-name:User}")
    private String lastName;

    @Override
    public void run(ApplicationArguments args) {
        String normalizedEmail = email.trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            return;
        }

        userRepository.save(User.builder()
                .firstName(firstName.trim())
                .lastName(lastName.trim())
                .email(normalizedEmail)
                .phone(phone.trim())
                .password(passwordEncoder.encode(password))
                .role(UserRole.ADMIN)
                .build());

        log.info("Stage admin kullanıcısı oluşturuldu: {}", normalizedEmail);
    }
}
