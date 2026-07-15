package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.UnauthorizedException;
import com.ael.algoryqrservice.model.enums.GoogleAuthIntent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class GoogleAuthSessionService {

    private static final String AUTH_INTENT_ATTRIBUTE = "googleAuthIntent";

    public void storeIntent(HttpServletRequest request, GoogleAuthIntent intent) {
        request.getSession(true).setAttribute(AUTH_INTENT_ATTRIBUTE, intent);
    }

    public GoogleAuthIntent requireIntent(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new UnauthorizedException("Google kimlik doğrulama oturumu bulunamadı");
        }
        Object intent = session.getAttribute(AUTH_INTENT_ATTRIBUTE);
        if (intent instanceof GoogleAuthIntent googleAuthIntent) {
            return googleAuthIntent;
        }
        throw new UnauthorizedException("Google kimlik doğrulama amacı bulunamadı");
    }

    public void clear(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
