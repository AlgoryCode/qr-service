package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.TrialDtos;
import com.ael.algoryqrservice.service.TrialService;
import com.ael.algoryqrservice.util.SecurityUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TrialControllerTest {
    @Test
    void status_whenAuthenticated_thenReturnCurrentUserLifecycle() {
        TrialService service = mock(TrialService.class);
        SecurityUtils securityUtils = mock(SecurityUtils.class);
        when(securityUtils.getCurrentUser()).thenReturn(User.builder().id(7L).build());
        TrialDtos.Status expected = new TrialDtos.Status(
                TrialDtos.Lifecycle.TRIAL_EXPIRED,
                4L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        when(service.status(7L)).thenReturn(expected);
        TrialController controller = new TrialController(service, securityUtils);

        TrialDtos.Status result = controller.status();

        assertThat(result).isEqualTo(expected);
        verify(service).status(7L);
    }
}
