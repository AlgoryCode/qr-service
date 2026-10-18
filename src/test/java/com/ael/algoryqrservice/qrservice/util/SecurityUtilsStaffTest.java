package com.ael.algoryqrservice.qrservice.util;

import com.ael.algoryqrservice.qrservice.account.service.JwtService;
import com.ael.algoryqrservice.qrservice.client.AuthMerchantClient;
import com.ael.algoryqrservice.staffservice.infrastructure.persistence.entity.MerchantStaff;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityUtilsStaffTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private MerchantStaffRepository merchantStaffRepository;
    @Mock
    private AuthMerchantClient authMerchantClient;

    @InjectMocks
    private SecurityUtils securityUtils;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void findCurrentWaiterId_returnsStaffIdNotMerchantId() {
        JwtAccessPrincipal principal = new JwtAccessPrincipal(
                58L,
                118L,
                null,
                null,
                List.of(),
                List.of(),
                null,
                JwtService.PRINCIPAL_WAITER,
                37L
        );
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "tako123123",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_WAITER"))
        );
        authentication.setDetails(principal);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(securityUtils.findCurrentWaiterId()).contains(58L);
    }

    @Test
    void getCurrentWaiterBranchId_usesBranchClaimWhenPresent() {
        JwtAccessPrincipal principal = new JwtAccessPrincipal(
                58L,
                118L,
                null,
                null,
                List.of(),
                List.of(),
                null,
                JwtService.PRINCIPAL_WAITER,
                37L
        );
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "tako123123",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_WAITER"))
        );
        authentication.setDetails(principal);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(securityUtils.getCurrentWaiterBranchId()).isEqualTo(37L);
    }

    @Test
    void getCurrentWaiterBranchId_loadsBranchFromStaffWhenClaimMissing() {
        JwtAccessPrincipal principal = new JwtAccessPrincipal(
                58L,
                118L,
                null,
                null,
                List.of(),
                List.of(),
                null,
                JwtService.PRINCIPAL_WAITER,
                null
        );
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "tako123123",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_KITCHEN"))
        );
        authentication.setDetails(principal);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        MerchantStaff staff = new MerchantStaff();
        staff.setId(58L);
        staff.setBranchId(37L);
        when(merchantStaffRepository.findById(58L)).thenReturn(Optional.of(staff));

        assertThat(securityUtils.getCurrentWaiterBranchId()).isEqualTo(37L);
    }
}
