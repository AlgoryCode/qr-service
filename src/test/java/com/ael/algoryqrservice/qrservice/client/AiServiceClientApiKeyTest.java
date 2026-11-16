package com.ael.algoryqrservice.qrservice.client;

import com.ael.algoryqrservice.qrservice.client.dto.AiBatchReportClientDtos;
import com.ael.algoryqrservice.qrservice.config.AiServiceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import com.sun.net.httpserver.HttpServer;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AiServiceClientApiKeyTest {

    @Test
    void applyApiKey_whenConfigured_thenSetsHeader() {
        AiServiceProperties properties = new AiServiceProperties();
        properties.setApiKey(" secret-key ");
        HttpHeaders headers = new HttpHeaders();

        AiServiceClient.applyApiKey(headers, properties);

        assertThat(headers.getFirst(AiServiceClient.API_KEY_HEADER)).isEqualTo("secret-key");
    }

    @Test
    void applyApiKey_whenBlank_thenOmitsHeader() {
        AiServiceProperties properties = new AiServiceProperties();
        HttpHeaders headers = new HttpHeaders();

        AiServiceClient.applyApiKey(headers, properties);

        assertThat(headers.containsHeader(AiServiceClient.API_KEY_HEADER)).isFalse();
    }

    @Test
    void createBatchReport_whenApiKeyConfigured_thenSendsItToAiService() throws Exception {
        AtomicReference<String> sentKey = new AtomicReference<>();
        AtomicReference<String> sentPath = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            sentKey.set(exchange.getRequestHeaders().getFirst(AiServiceClient.API_KEY_HEADER));
            sentPath.set(exchange.getRequestURI().getPath());
            byte[] body = "{\"openaiBatchId\":\"batch_1\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            AiServiceProperties properties = new AiServiceProperties();
            properties.setUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.setApiKey("secret-key");

            AiBatchReportClientDtos.CreateResponse response = new AiServiceClient(RestClient.builder(), properties)
                    .createBatchReport(AiBatchReportClientDtos.CreateRequest.builder()
                            .items(List.of(AiBatchReportClientDtos.Item.builder()
                                    .customId("item-1")
                                    .payload(Map.of("visits", Map.of()))
                                    .build()))
                            .build());

            assertThat(response.getOpenaiBatchId()).isEqualTo("batch_1");
            assertThat(sentPath.get()).isEqualTo("/api/v1/batch-reports");
            assertThat(sentKey.get()).isEqualTo("secret-key");
        } finally {
            server.stop(0);
        }
    }
}
