package com.ael.algoryqrservice.util;

import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.repository.CustomerRepository;
import com.ael.algoryqrservice.repository.MerchantStaffRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.security.JwtAccessPrincipal;
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
    void getCurrentUserId_whenEmailAccountDiffersFromToken_thenReturnsAccountId() {
        when(userRepository.findByEmail("owner@example.com"))
                .thenReturn(Optional.of(User.builder().id(7L).build()));
        authenticate(99L, "owner@example.com");

        assertThat(securityUtils.getCurrentUserId()).isEqualTo(7L);
    }

    @Test
    void getCurrentUserId_whenEmailAccountMissing_thenReturnsTokenId() {
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.empty());
        authenticate(99L, "owner@example.com");

        assertThat(securityUtils.getCurrentUserId()).isEqualTo(99L);
    }

    private void authenticate(Long tokenUserId, String email) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                email,
                null,
                List.of()
        );
        authentication.setDetails(new JwtAccessPrincipal(tokenUserId, List.of(), List.of(), null));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
