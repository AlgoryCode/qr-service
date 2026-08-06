package com.ael.algoryqrservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    private String filerUrl = "http://localhost:8888";
    private String s3Endpoint = "";
    private String accessKey = "";
    private String secretKey = "";
    private String bucket = "qr-product-images";
    private String publicBaseUrl = "http://localhost:8888/buckets/qr-product-images";
    private long maxFileSizeBytes = 5_242_880L;
    private List<String> allowedContentTypes = List.of("image/jpeg", "image/png", "image/webp");

    public boolean isS3Mode() {
        return accessKey != null && !accessKey.isBlank()
                && secretKey != null && !secretKey.isBlank()
                && s3Endpoint != null && !s3Endpoint.isBlank();
    }
}
