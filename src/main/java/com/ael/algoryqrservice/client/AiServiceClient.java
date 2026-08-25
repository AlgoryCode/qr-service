package com.ael.algoryqrservice.client;

import com.ael.algoryqrservice.client.dto.MenuProductReindexDtos;
import com.ael.algoryqrservice.config.AiServiceProperties;
import com.ael.algoryqrservice.messaging.dto.MenuProductDocumentMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Bulk backfill channel to the vector indexer. Incremental changes travel over RabbitMQ;
 * this client exists for full menu reindex, which also needs stale-point purging.
 */
@Slf4j
@Component
public class AiServiceClient {

    private static final String REINDEX_PATH = "/api/v1/menu-products/reindex";
    private static final String API_KEY_HEADER = "X-API-Key";

    private final RestClient restClient;
    private final AiServiceProperties properties;

    public AiServiceClient(RestClient.Builder restClientBuilder, AiServiceProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.getUrl())
                .requestFactory(requestFactory(properties))
                .build();
    }

    private static SimpleClientHttpRequestFactory requestFactory(AiServiceProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        return factory;
    }

    public MenuProductReindexDtos.Response reindex(
            List<MenuProductDocumentMessage> documents,
            Long purgeMissingForMenuId,
            List<Long> keepProductIds
    ) {
        return restClient.post()
                .uri(REINDEX_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(API_KEY_HEADER, properties.getApiKey())
                .body(new MenuProductReindexDtos.Request(
                        documents,
                        purgeMissingForMenuId,
                        keepProductIds
                ))
                .retrieve()
                .body(MenuProductReindexDtos.Response.class);
    }
}
