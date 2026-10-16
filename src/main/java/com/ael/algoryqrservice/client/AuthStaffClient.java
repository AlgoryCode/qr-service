package com.ael.algoryqrservice.client;

import com.ael.algoryqrservice.config.AuthServiceClientProperties;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.MerchantStaff;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthStaffClient {

    private static final String STAFF_PATH = "/internal/staff/";

    private final RestClient.Builder restClientBuilder;
    private final AuthServiceClientProperties properties;
    private final ObjectMapper objectMapper;

    public void upsert(MerchantStaff staff) {
        if (!properties.isEnabled() || staff == null || staff.getId() == null) {
            return;
        }
        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BadRequestException("Auth servisi adresi tanımlı değil");
        }
        UpsertBody body = new UpsertBody(
                staff.getMerchantId(),
                staff.resolvedStaffRole().name(),
                staff.getBranchId(),
                staff.getUsername(),
                staff.getPasswordHash(),
                staff.getDisplayName(),
                staff.isActive(),
                staff.isCommissionEnabled(),
                staff.getCommissionType() == null ? null : staff.getCommissionType().name(),
                staff.getCommissionValue()
        );
        try {
            restClientBuilder.build()
                    .put()
                    .uri(trimSlash(baseUrl) + STAFF_PATH + staff.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            String message = readMessage(exception.getResponseBodyAsString(), "Personel auth kaydı yazılamadı");
            log.warn("Auth staff upsert failed. staffId={} status={}", staff.getId(), exception.getStatusCode().value());
            throw new BadRequestException(message);
        } catch (RestClientException exception) {
            log.warn("Auth staff upsert unavailable. staffId={} reason={}", staff.getId(), exception.getMessage());
            throw new BadRequestException("Auth servisi kullanılamıyor");
        }
    }

    private String readMessage(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node.hasNonNull("message")) {
                return node.get("message").asText();
            }
        } catch (Exception exception) {
            log.debug("Auth staff error body was not json");
        }
        return fallback;
    }

    private static String trimSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private record UpsertBody(
            Long merchantId,
            String role,
            Long branchId,
            String username,
            String passwordHash,
            String displayName,
            boolean active,
            boolean commissionEnabled,
            String commissionType,
            BigDecimal commissionValue
    ) {
    }
}
