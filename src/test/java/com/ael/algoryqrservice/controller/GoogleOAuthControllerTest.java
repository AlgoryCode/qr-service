package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.exception.GlobalExceptionHandler;
import com.ael.algoryqrservice.model.dto.AuthResponse;
import com.ael.algoryqrservice.model.enums.GoogleAuthIntent;
import com.ael.algoryqrservice.service.GoogleAuthHandoffService;
import com.ael.algoryqrservice.service.GoogleAuthSessionService;
import com.ael.algoryqrservice.util.ClientInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GoogleOAuthControllerTest {

    @Mock
    private GoogleAuthSessionService authSessionService;
    @Mock
    private GoogleAuthHandoffService handoffService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new GoogleAuthController(authSessionService, handoffService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void authorize_whenIntentIsLogin_thenStoreIntentAndRedirect() throws Exception {
        mockMvc.perform(get("/google-auth/authorize").param("intent", "login"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/oauth2/authorization/google"));

        verify(authSessionService).storeIntent(any(), eq(GoogleAuthIntent.LOGIN));
    }

    @Test
    void authorize_whenIntentIsInvalid_thenReturnBadRequest() throws Exception {
        mockMvc.perform(get("/google-auth/authorize").param("intent", "admin"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Google kimlik doğrulama amacı geçersiz"));
    }

    @Test
    void redeem_whenTicketIsValid_thenReturnJwtTokens() throws Exception {
        when(handoffService.redeem(eq("opaque-ticket"), any(ClientInfo.class)))
                .thenReturn(AuthResponse.builder()
                        .accessToken("access")
                        .refreshToken("refresh")
                        .build());

        mockMvc.perform(post("/google-auth/redeem")
                        .contentType("application/json")
                        .content("""
                                {
                                  "ticket": "opaque-ticket"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"));
    }
}
