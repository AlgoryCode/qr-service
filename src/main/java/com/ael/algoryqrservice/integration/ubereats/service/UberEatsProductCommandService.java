package com.ael.algoryqrservice.integration.ubereats.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.integration.ubereats.client.UberEatsClient;
import com.ael.algoryqrservice.integration.ubereats.mapper.UberEatsPayloadMapper;
import com.ael.algoryqrservice.integration.ubereats.model.UberEatsConnection;
import com.ael.algoryqrservice.integration.ubereats.model.dto.UberEatsDtos;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class UberEatsProductCommandService {

    private final UberEatsConnectionService connectionService;
    private final UberEatsClient uberEatsClient;
    private final UberEatsPayloadMapper payloadMapper;

    public UberEatsDtos.ProductResponse create(UberEatsDtos.CreateProductRequest request) {
        validate(request);
        return upsert(payloadMapper.toUpsertBody(request), request);
    }

    public UberEatsDtos.ProductResponse update(String productId, UberEatsDtos.CreateProductRequest request) {
        if (productId == null || productId.isBlank()) {
            throw new BadRequestException("productId zorunludur");
        }
        validate(request);
        return upsert(payloadMapper.toUpdateBody(productId, request), request);
    }

    private UberEatsDtos.ProductResponse upsert(Map<String, Object> body, UberEatsDtos.CreateProductRequest request) {
        UberEatsConnection connection = connectionService.requireConnected();
        UberEatsDtos.Credentials credentials = connectionService.decrypt(connection);
        JsonNode response = uberEatsClient.upsertMenuProduct(credentials, body);
        return payloadMapper.toCreatedProduct(response, request);
    }

    private void validate(UberEatsDtos.CreateProductRequest request) {
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new BadRequestException("name zorunludur");
        }
        if (request.getCategoryName() == null || request.getCategoryName().isBlank()) {
            throw new BadRequestException("Kategori zorunludur");
        }
    }
}
