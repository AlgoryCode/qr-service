package com.ael.algoryqrservice.integration.ubereatsmenu.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
public class UberEatsMenuClientConfig {

    @Bean
    @Qualifier("uberEatsMenuRestClient")
    public RestClient uberEatsMenuRestClient(UberEatsMenuProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder()
                .baseUrl(trimSlash(properties.getApiBaseUrl()))
                .requestFactory(factory)
                .build();
    }

    @Bean
    @Qualifier("uberEatsAuthRestClient")
    public RestClient uberEatsAuthRestClient(UberEatsMenuProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    private String trimSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
