package com.ael.algoryqrservice.qrservice.client;

import com.ael.algoryqrservice.qrservice.client.dto.AiBatchReportClientDtos;
import com.ael.algoryqrservice.qrservice.client.dto.AiMenuImportClientDtos;
import com.ael.algoryqrservice.qrservice.client.dto.MenuProductReindexDtos;
import com.ael.algoryqrservice.qrservice.config.AiServiceProperties;
import com.ael.algoryqrservice.qrservice.product.dto.MenuProductDocumentMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class AiServiceClient {

    private static final String REINDEX_PATH = "/api/v1/menu-products/reindex";
    private static final String MENU_IMPORT_PATH = "/api/v1/menu-import";
    private static final String BATCH_REPORTS_PATH = "/api/v1/batch-reports";
    private static final Duration BATCH_READ_TIMEOUT = Duration.ofSeconds(180);
    static final String API_KEY_HEADER = "X-API-Key";

    private final RestClient restClient;
    private final RestClient batchRestClient;
    private final AiServiceProperties properties;

    public AiServiceClient(RestClient.Builder restClientBuilder, AiServiceProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .clone()
                .baseUrl(properties.getUrl())
                .defaultHeaders(headers -> applyApiKey(headers, properties))
                .requestFactory(requestFactory(properties.getConnectTimeout(), properties.getReadTimeout()))
                .build();
        this.batchRestClient = restClientBuilder
                .clone()
                .baseUrl(properties.getUrl())
                .defaultHeaders(headers -> applyApiKey(headers, properties))
                .requestFactory(requestFactory(properties.getConnectTimeout(), BATCH_READ_TIMEOUT))
                .build();
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.warn("AI_SERVICE_API_KEY is not set: ai-service will reject batch, import and reindex calls");
        }
    }

    static void applyApiKey(HttpHeaders headers, AiServiceProperties properties) {
        String apiKey = properties.getApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            headers.set(API_KEY_HEADER, apiKey.trim());
        }
    }

    private static SimpleClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
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
                .body(new MenuProductReindexDtos.Request(
                        documents,
                        purgeMissingForMenuId,
                        keepProductIds
                ))
                .retrieve()
                .body(MenuProductReindexDtos.Response.class);
    }

    public AiMenuImportClientDtos.JobAccepted createMenuImportJob(AiMenuImportClientDtos.CreateRequest request) {
        log.info(
                "ai_menu_import_proxy_create menuId={} merchantId={} imageCount={}",
                request.getMenuId(),
                request.getMerchantId(),
                request.getImageUrls() == null ? 0 : request.getImageUrls().size()
        );
        return restClient.post()
                .uri(MENU_IMPORT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(AiMenuImportClientDtos.JobAccepted.class);
    }

    public AiMenuImportClientDtos.JobResponse getMenuImportJob(UUID jobId) {
        return restClient.get()
                .uri(MENU_IMPORT_PATH + "/{jobId}", jobId)
                .retrieve()
                .body(AiMenuImportClientDtos.JobResponse.class);
    }

    public AiBatchReportClientDtos.CreateResponse createBatchReport(AiBatchReportClientDtos.CreateRequest request) {
        log.info(
                "ai_batch_report_create itemCount={}",
                request.getItems() == null ? 0 : request.getItems().size()
        );
        return batchRestClient.post()
                .uri(BATCH_REPORTS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(AiBatchReportClientDtos.CreateResponse.class);
    }

    public AiBatchReportClientDtos.StatusResponse getBatchReportStatus(String openaiBatchId) {
        return restClient.get()
                .uri(BATCH_REPORTS_PATH + "/{id}/status", openaiBatchId)
                .retrieve()
                .body(AiBatchReportClientDtos.StatusResponse.class);
    }

    public List<AiBatchReportClientDtos.ResultItem> getBatchReportResults(String openaiBatchId) {
        AiBatchReportClientDtos.ResultItem[] body = batchRestClient.get()
                .uri(BATCH_REPORTS_PATH + "/{id}/results", openaiBatchId)
                .retrieve()
                .body(AiBatchReportClientDtos.ResultItem[].class);
        if (body == null || body.length == 0) {
            return Collections.emptyList();
        }
        return Arrays.asList(body);
    }
}
