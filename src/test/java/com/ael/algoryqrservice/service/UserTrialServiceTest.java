package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserTrialServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    PurchaseRepository purchaseRepository;
    @InjectMocks
    UserTrialService service;

    @Test
    void hasUsedTrial_whenTrialEndDateSet_thenTrue() {
        User user = User.builder().trialEndDate(LocalDateTime.now()).build();
        assertThat(service.hasUsedTrial(user)).isTrue();
    }

    @Test
    void markTrialCompleted_whenFirstTime_thenPersistEndDateAndFlag() {
        User user = User.builder().id(7L).trialUsed(false).build();
        LocalDateTime endDate = LocalDateTime.of(2026, 8, 1, 12, 0);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        service.markTrialCompleted(7L, endDate);

        assertThat(user.getTrialEndDate()).isEqualTo(endDate);
        assertThat(user.isTrialUsed()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void resetTrialEligibility_thenClearFlags() {
        User user = User.builder()
                .id(7L)
                .trialUsed(true)
                .trialEndDate(LocalDateTime.of(2026, 8, 1, 12, 0))
                .build();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        service.resetTrialEligibility(7L);

        assertThat(user.isTrialUsed()).isFalse();
        assertThat(user.getTrialEndDate()).isNull();
        verify(userRepository).save(user);
    }
}
