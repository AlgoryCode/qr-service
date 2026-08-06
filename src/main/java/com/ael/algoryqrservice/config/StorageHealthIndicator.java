package com.ael.algoryqrservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;

@Component
@RequiredArgsConstructor
public class StorageHealthIndicator implements HealthIndicator {

    private final RestClient.Builder restClientBuilder;
    private final StorageProperties storageProperties;

    @Override
    public Health health() {
        if (storageProperties.isS3Mode()) {
            return healthS3();
        }
        return healthFiler();
    }

    private Health healthS3() {
        try (S3Client client = S3Client.builder()
                .endpointOverride(URI.create(trimTrailingSlash(storageProperties.getS3Endpoint())))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                storageProperties.getAccessKey(),
                                storageProperties.getSecretKey()
                        )
                ))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false)
                        .build())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build()) {
            client.headBucket(HeadBucketRequest.builder()
                    .bucket(storageProperties.getBucket())
                    .build());
            return Health.up()
                    .withDetail("mode", "s3")
                    .withDetail("endpoint", storageProperties.getS3Endpoint())
                    .withDetail("bucket", storageProperties.getBucket())
                    .build();
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return Health.up()
                        .withDetail("mode", "s3")
                        .withDetail("endpoint", storageProperties.getS3Endpoint())
                        .withDetail("bucket", storageProperties.getBucket())
                        .withDetail("bucketExists", false)
                        .build();
            }
            return Health.down(exception)
                    .withDetail("mode", "s3")
                    .withDetail("endpoint", storageProperties.getS3Endpoint())
                    .withDetail("bucket", storageProperties.getBucket())
                    .build();
        } catch (RuntimeException exception) {
            return Health.down(exception)
                    .withDetail("mode", "s3")
                    .withDetail("endpoint", storageProperties.getS3Endpoint())
                    .withDetail("bucket", storageProperties.getBucket())
                    .build();
        }
    }

    private Health healthFiler() {
        try {
            restClientBuilder
                    .baseUrl(trimTrailingSlash(storageProperties.getFilerUrl()))
                    .build()
                    .get()
                    .uri("/")
                    .retrieve()
                    .toBodilessEntity();
            return Health.up()
                    .withDetail("mode", "filer")
                    .withDetail("filerUrl", storageProperties.getFilerUrl())
                    .withDetail("bucket", storageProperties.getBucket())
                    .build();
        } catch (RestClientException exception) {
            return Health.down(exception)
                    .withDetail("mode", "filer")
                    .withDetail("filerUrl", storageProperties.getFilerUrl())
                    .withDetail("bucket", storageProperties.getBucket())
                    .build();
        }
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
