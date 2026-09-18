package com.ael.algoryqrservice.store.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The order endpoint is unauthenticated, so a caller identified by IP plus phone number
 * gets a fixed budget per window before being turned away.
 */
@Component
public class StoreOrderRateLimiter {

    private static final int MAX_ORDERS_PER_WINDOW = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    public void check(String clientIp, String phone) {
        String key = (clientIp == null ? "unknown" : clientIp) + "|" + (phone == null ? "" : phone.trim());
        Instant now = Instant.now();
        Deque<Instant> window = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst().isBefore(now.minus(WINDOW))) {
                window.pollFirst();
            }
            if (window.size() >= MAX_ORDERS_PER_WINDOW) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Çok fazla sipariş denemesi");
            }
            window.addLast(now);
        }
        evictIfOversized(now);
    }

    private void evictIfOversized(Instant now) {
        if (attempts.size() <= MAX_TRACKED_KEYS) {
            return;
        }
        attempts.entrySet().removeIf(entry -> {
            Deque<Instant> window = entry.getValue();
            synchronized (window) {
                return window.isEmpty() || window.peekLast().isBefore(now.minus(WINDOW));
            }
        });
    }
}
