package com.ael.algoryqrservice.qrservice.util;

import com.ael.algoryqrservice.qrservice.model.User;
import com.ael.algoryqrservice.customerservice.infrastructure.persistence.repository.CustomerRepository;
import com.ael.algoryqrservice.staffservice.infrastructure.persistence.repository.MerchantStaffRepository;
import com.ael.algoryqrservice.qrservice.repository.UserRepository;
import com.ael.algoryqrservice.qrservice.security.JwtAccessPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityUtilsAccountUserTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private MerchantStaffRepository merchantStaffRepository;
    @InjectMocks
    private SecurityUtils securityUtils;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentMerchantId_whenTokenHasMerchantId_thenReturnsTokenMerchantId() {
        authenticate(99L, "owner@example.com");

        assertThat(securityUtils.getCurrentMerchantId()).isEqualTo(99L);
        verifyNoInteractions(userRepository, merchantStaffRepository);
    }

    private void authenticate(Long tokenUserId, String email) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                email,
                null,
                List.of()
        );
        authentication.setDetails(new JwtAccessPrincipal(null, tokenUserId, null, null, List.of(), List.of(), null, null, null));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
