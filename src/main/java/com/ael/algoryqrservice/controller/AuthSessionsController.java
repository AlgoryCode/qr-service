package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.model.dto.SessionPageResponse;
import com.ael.algoryqrservice.service.AccountFacadeService;
import com.ael.algoryqrservice.util.HttpRequestAuth;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Legacy path documented in auth-gateway.md. Delegates to the same logic as {@code GET /account/sessions}.
 */
@RestController
@RequestMapping("/auth/sessions")
@RequiredArgsConstructor
public class AuthSessionsController {

    private final AccountFacadeService accountFacade;

    @GetMapping
    public SessionPageResponse list(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return accountFacade.listSessions(HttpRequestAuth.readBearerToken(request), page, size);
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> revoke(@PathVariable UUID sessionId) {
        accountFacade.revokeSession(sessionId);
        return ResponseEntity.noContent().build();
    }
}
