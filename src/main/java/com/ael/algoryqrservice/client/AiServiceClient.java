package com.ael.algoryqrservice.client;

import com.ael.algoryqrservice.client.dto.AiBatchReportClientDtos;
import com.ael.algoryqrservice.client.dto.AiMenuImportClientDtos;
import com.ael.algoryqrservice.client.dto.MenuProductReindexDtos;
import com.ael.algoryqrservice.config.AiServiceProperties;
import com.ael.algoryqrservice.messaging.dto.MenuProductDocumentMessage;
import lombok.extern.slf4j.Slf4j;
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
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final Duration BATCH_READ_TIMEOUT = Duration.ofSeconds(180);

    private final RestClient restClient;
    private final RestClient batchRestClient;
    private final AiServiceProperties properties;

    public AiServiceClient(RestClient.Builder restClientBuilder, AiServiceProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .clone()
                .baseUrl(properties.getUrl())
                .requestFactory(requestFactory(properties.getConnectTimeout(), properties.getReadTimeout()))
                .build();
        this.batchRestClient = restClientBuilder
                .clone()
                .baseUrl(properties.getUrl())
                .requestFactory(requestFactory(properties.getConnectTimeout(), BATCH_READ_TIMEOUT))
                .build();
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
                .header(API_KEY_HEADER, properties.getApiKey())
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
                "ai_menu_import_proxy_create menuId={} userId={} imageCount={}",
                request.getMenuId(),
                request.getUserId(),
                request.getImageUrls() == null ? 0 : request.getImageUrls().size()
        );
        return restClient.post()
                .uri(MENU_IMPORT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(API_KEY_HEADER, properties.getApiKey())
                .body(request)
                .retrieve()
                .body(AiMenuImportClientDtos.JobAccepted.class);
    }

    public AiMenuImportClientDtos.JobResponse getMenuImportJob(UUID jobId) {
        return restClient.get()
                .uri(MENU_IMPORT_PATH + "/{jobId}", jobId)
                .header(API_KEY_HEADER, properties.getApiKey())
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
                .header(API_KEY_HEADER, properties.getApiKey())
                .body(request)
                .retrieve()
                .body(AiBatchReportClientDtos.CreateResponse.class);
    }

    public AiBatchReportClientDtos.StatusResponse getBatchReportStatus(String openaiBatchId) {
        return restClient.get()
                .uri(BATCH_REPORTS_PATH + "/{id}/status", openaiBatchId)
                .header(API_KEY_HEADER, properties.getApiKey())
                .retrieve()
                .body(AiBatchReportClientDtos.StatusResponse.class);
    }

    public List<AiBatchReportClientDtos.ResultItem> getBatchReportResults(String openaiBatchId) {
        AiBatchReportClientDtos.ResultItem[] body = batchRestClient.get()
                .uri(BATCH_REPORTS_PATH + "/{id}/results", openaiBatchId)
                .header(API_KEY_HEADER, properties.getApiKey())
                .retrieve()
                .body(AiBatchReportClientDtos.ResultItem[].class);
        if (body == null || body.length == 0) {
            return Collections.emptyList();
        }
        return Arrays.asList(body);
    }
}
