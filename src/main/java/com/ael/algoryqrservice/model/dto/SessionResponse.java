package com.ael.algoryqrservice.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionResponse {

    private UUID sessionId;
    private LocalDateTime loggedInAt;
    private LocalDateTime lastActivityAt;
    private LocalDateTime accessExpiresAt;
    private LocalDateTime refreshExpiresAt;
    private boolean revoked;
    private LocalDateTime revokedAt;
    private boolean expired;
    private boolean active;
    private String ipAddress;
    private String userAgent;
    private String device;
    private String deviceType;
}
