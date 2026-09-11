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

    /**
     * Creates a product on the connected Uber Eats partner menu.
     *
     * @param request validated item payload
     * @return partner product after upsert
     */
    public UberEatsDtos.ProductResponse create(UberEatsDtos.CreateProductRequest request) {
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new BadRequestException("name zorunludur");
        }
        if (request.getCategoryName() == null || request.getCategoryName().isBlank()) {
            throw new BadRequestException("Kategori zorunludur");
        }
        UberEatsConnection connection = connectionService.requireConnected();
        UberEatsDtos.Credentials credentials = connectionService.decrypt(connection);
        Map<String, Object> body = payloadMapper.toUpsertBody(request);
        JsonNode response = uberEatsClient.upsertMenuProduct(credentials, body);
        return payloadMapper.toCreatedProduct(response, request);
    }
}
