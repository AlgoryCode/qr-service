package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.config.StorageProperties;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.dto.ProductImageDtos;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImageStorageService {

    private final RestClient.Builder restClientBuilder;
    private final StorageProperties storageProperties;
    private final AtomicBoolean bucketReady = new AtomicBoolean(false);
    private final Object s3Lock = new Object();
    private volatile S3Client s3Client;

    public ProductImageDtos.UploadResponse upload(Long menuId, MultipartFile file) {
        return uploadWithPrefix(menuId, file, "menus/" + menuId + "/");
    }

    public ProductImageDtos.UploadResponse uploadLogo(Long menuId, MultipartFile file) {
        return uploadWithPrefix(menuId, file, "menus/" + menuId + "/logo/");
    }

    public ProductImageDtos.UploadResponse uploadFeedbackScreenshot(Long userId, MultipartFile file) {
        return uploadWithPrefix(userId, file, "platform-feedback/" + userId + "/");
    }

    public void deleteForMenu(Long menuId, String objectKey, String imageUrl) {
        String resolvedKey = resolveObjectKey(objectKey, imageUrl);
        if (resolvedKey == null || resolvedKey.isBlank()) {
            throw new BadRequestException("objectKey veya imageUrl zorunludur");
        }
        if (!resolvedKey.startsWith("menus/" + menuId + "/")) {
            throw new BadRequestException("Bu görsel bu menüye ait değil");
        }
        delete(resolvedKey);
    }

    public void deleteLogoForMenu(Long menuId, String objectKey, String imageUrl) {
        String resolvedKey = resolveObjectKey(objectKey, imageUrl);
        if (resolvedKey == null || resolvedKey.isBlank()) {
            throw new BadRequestException("objectKey veya imageUrl zorunludur");
        }
        if (!resolvedKey.startsWith("menus/" + menuId + "/logo/")) {
            throw new BadRequestException("Bu logo bu menüye ait değil");
        }
        delete(resolvedKey);
    }

    private ProductImageDtos.UploadResponse uploadWithPrefix(Long menuId, MultipartFile file, String keyPrefix) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Görsel dosyası zorunludur");
        }
        if (file.getSize() > storageProperties.getMaxFileSizeBytes()) {
            throw new BadRequestException("Görsel boyutu en fazla 5 MB olabilir");
        }

        String contentType = resolveContentType(file);
        if (!storageProperties.getAllowedContentTypes().contains(contentType)) {
            throw new BadRequestException("Desteklenmeyen görsel formatı");
        }

        byte[] bytes = readBytes(file);
        validateMagicBytes(bytes, contentType);

        String extension = extensionForContentType(contentType);
        String objectKey = keyPrefix + UUID.randomUUID() + "." + extension;

        if (storageProperties.isS3Mode()) {
            uploadViaS3(objectKey, bytes, contentType);
        } else {
            uploadViaFiler(objectKey, bytes, contentType);
        }

        return new ProductImageDtos.UploadResponse(buildPublicUrl(objectKey), objectKey);
    }

    public void delete(String objectKey) {
        if (storageProperties.isS3Mode()) {
            deleteViaS3(objectKey);
            return;
        }
        filerClient().delete()
                .uri(filerObjectPath(objectKey))
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), (request, response) -> {
                    throw new BadRequestException("Görsel silinemedi");
                })
                .toBodilessEntity();
    }

    public void deleteQuietly(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            delete(objectKey);
        } catch (Exception exception) {
            if (log.isWarnEnabled()) {
                log.warn("Görsel silinemedi: {}", objectKey, exception);
            }
        }
    }

    public void uploadBytes(String objectKey, byte[] bytes, String contentType) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new BadRequestException("objectKey zorunludur");
        }
        if (bytes == null || bytes.length == 0) {
            throw new BadRequestException("Görsel dosyası zorunludur");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new BadRequestException("Görsel içerik tipi belirlenemedi");
        }
        String normalizedType = contentType.toLowerCase(Locale.ROOT);
        if (!storageProperties.getAllowedContentTypes().contains(normalizedType)) {
            throw new BadRequestException("Desteklenmeyen görsel formatı");
        }
        validateMagicBytes(bytes, normalizedType);
        if (storageProperties.isS3Mode()) {
            uploadViaS3(objectKey, bytes, normalizedType);
        } else {
            uploadViaFiler(objectKey, bytes, normalizedType);
        }
    }

    public boolean exists(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return false;
        }
        if (storageProperties.isS3Mode()) {
            return existsViaS3(objectKey);
        }
        return existsViaFiler(objectKey);
    }

    public void validateImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }
        String normalizedBaseUrl = trimTrailingSlash(storageProperties.getPublicBaseUrl());
        if (!imageUrl.startsWith(normalizedBaseUrl + "/")) {
            throw new BadRequestException("Geçersiz görsel URL");
        }
    }

    public String extractObjectKey(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        String normalizedBaseUrl = trimTrailingSlash(storageProperties.getPublicBaseUrl());
        String prefix = normalizedBaseUrl + "/";
        if (!imageUrl.startsWith(prefix)) {
            return null;
        }
        return imageUrl.substring(prefix.length());
    }

    public String buildPublicUrl(String objectKey) {
        return trimTrailingSlash(storageProperties.getPublicBaseUrl()) + "/" + objectKey;
    }

    @PreDestroy
    void closeS3Client() {
        S3Client client = s3Client;
        if (client != null) {
            client.close();
        }
    }

    private void uploadViaS3(String objectKey, byte[] bytes, String contentType) {
        S3Client client = s3Client();
        ensureBucketExists(client);
        try {
            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(storageProperties.getBucket())
                            .key(objectKey)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(bytes)
            );
        } catch (S3Exception exception) {
            if (log.isErrorEnabled()) {
                log.error("S3 upload rejected. endpoint={} bucket={} key={} status={} message={}",
                        storageProperties.getS3Endpoint(),
                        storageProperties.getBucket(),
                        objectKey,
                        exception.statusCode(),
                        exception.awsErrorDetails() != null ? exception.awsErrorDetails().errorMessage() : exception.getMessage());
            }
            throw new BadRequestException("Görsel yüklenemedi");
        } catch (SdkClientException exception) {
            if (log.isErrorEnabled()) {
                log.error("S3 upload connection failed. endpoint={} bucket={} key={} message={}",
                        storageProperties.getS3Endpoint(), storageProperties.getBucket(), objectKey, exception.getMessage(), exception);
            }
            throw new BadRequestException("Depolama servisine bağlanılamadı");
        } catch (RuntimeException exception) {
            if (log.isErrorEnabled()) {
                log.error("S3 upload failed. endpoint={} bucket={} key={}",
                        storageProperties.getS3Endpoint(), storageProperties.getBucket(), objectKey, exception);
            }
            throw new BadRequestException("Depolama servisine bağlanılamadı");
        }
    }

    private void deleteViaS3(String objectKey) {
        try {
            s3Client().deleteObject(DeleteObjectRequest.builder()
                    .bucket(storageProperties.getBucket())
                    .key(objectKey)
                    .build());
        } catch (S3Exception exception) {
            throw new BadRequestException("Görsel silinemedi");
        } catch (RuntimeException exception) {
            throw new BadRequestException("Depolama servisine bağlanılamadı");
        }
    }

    private boolean existsViaS3(String objectKey) {
        try {
            S3Client client = s3Client();
            ensureBucketExists(client);
            client.headObject(HeadObjectRequest.builder()
                    .bucket(storageProperties.getBucket())
                    .key(objectKey)
                    .build());
            return true;
        } catch (NoSuchKeyException exception) {
            return false;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            if (log.isWarnEnabled()) {
                log.warn("S3 headObject failed. key={} status={}", objectKey, exception.statusCode());
            }
            return false;
        } catch (RuntimeException exception) {
            if (log.isWarnEnabled()) {
                log.warn("S3 headObject connection failed. key={}", objectKey, exception);
            }
            return false;
        }
    }

    private boolean existsViaFiler(String objectKey) {
        String filerPath = filerObjectPath(objectKey);
        try {
            filerClient().head()
                    .uri(filerPath)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException exception) {
            return false;
        }
    }

    private void ensureBucketExists(S3Client client) {
        if (bucketReady.get()) {
            return;
        }
        String bucket = storageProperties.getBucket();
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            bucketReady.set(true);
        } catch (NoSuchBucketException exception) {
            createBucket(client, bucket);
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                createBucket(client, bucket);
                return;
            }
            if (log.isErrorEnabled()) {
                log.error("S3 headBucket failed. endpoint={} bucket={} status={} message={}",
                        storageProperties.getS3Endpoint(),
                        bucket,
                        exception.statusCode(),
                        exception.awsErrorDetails() != null ? exception.awsErrorDetails().errorMessage() : exception.getMessage(),
                        exception);
            }
            throw new BadRequestException("Depolama servisine bağlanılamadı");
        } catch (SdkClientException exception) {
            if (log.isErrorEnabled()) {
                log.error("S3 headBucket connection failed. endpoint={} bucket={} message={}",
                        storageProperties.getS3Endpoint(), bucket, exception.getMessage(), exception);
            }
            throw new BadRequestException("Depolama servisine bağlanılamadı");
        }
    }

    private void createBucket(S3Client client, String bucket) {
        try {
            client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            bucketReady.set(true);
        } catch (S3Exception exception) {
            if (log.isErrorEnabled()) {
                log.error("S3 bucket create failed. endpoint={} bucket={} status={} message={}",
                        storageProperties.getS3Endpoint(),
                        bucket,
                        exception.statusCode(),
                        exception.awsErrorDetails() != null ? exception.awsErrorDetails().errorMessage() : exception.getMessage(),
                        exception);
            }
            throw new BadRequestException("Görsel yüklenemedi");
        } catch (SdkClientException exception) {
            if (log.isErrorEnabled()) {
                log.error("S3 bucket create connection failed. endpoint={} bucket={} message={}",
                        storageProperties.getS3Endpoint(), bucket, exception.getMessage(), exception);
            }
            throw new BadRequestException("Depolama servisine bağlanılamadı");
        }
    }

    private S3Client s3Client() {
        S3Client existing = s3Client;
        if (existing != null) {
            return existing;
        }
        synchronized (s3Lock) {
            if (s3Client == null) {
                try {
                    s3Client = S3Client.builder()
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
                            .build();
                } catch (RuntimeException exception) {
                    if (log.isErrorEnabled()) {
                        log.error("S3 client init failed. endpoint={}", storageProperties.getS3Endpoint(), exception);
                    }
                    throw new BadRequestException("Depolama servisine bağlanılamadı");
                }
            }
            return s3Client;
        }
    }

    private void uploadViaFiler(String objectKey, byte[] bytes, String contentType) {
        String filerPath = filerObjectPath(objectKey);
        try {
            filerClient().put()
                    .uri(filerPath)
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(bytes)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), (request, response) -> {
                        String body = readResponseBody(response.getBody());
                        if (log.isErrorEnabled()) {
                            log.error("SeaweedFS upload rejected. filerUrl={} path={} status={} body={}",
                                    storageProperties.getFilerUrl(), filerPath, response.getStatusCode().value(), body);
                        }
                        throw new BadRequestException("Görsel yüklenemedi");
                    })
                    .toBodilessEntity();
        } catch (BadRequestException exception) {
            throw exception;
        } catch (RestClientException exception) {
            if (log.isErrorEnabled()) {
                log.error("SeaweedFS upload failed. filerUrl={} path={}",
                        storageProperties.getFilerUrl(), filerPath, exception);
            }
            throw new BadRequestException("Depolama servisine bağlanılamadı");
        }
    }

    private RestClient filerClient() {
        return restClientBuilder
                .baseUrl(trimTrailingSlash(storageProperties.getFilerUrl()))
                .build();
    }

    private String filerObjectPath(String objectKey) {
        return "/buckets/" + storageProperties.getBucket() + "/" + objectKey;
    }

    private String resolveObjectKey(String objectKey, String imageUrl) {
        if (objectKey != null && !objectKey.isBlank()) {
            return objectKey.trim();
        }
        return extractObjectKey(imageUrl);
    }

    private String resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            throw new BadRequestException("Görsel içerik tipi belirlenemedi");
        }
        return contentType.toLowerCase(Locale.ROOT);
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new BadRequestException("Görsel okunamadı");
        }
    }

    private void validateMagicBytes(byte[] bytes, String contentType) {
        if (bytes.length < 12) {
            throw new BadRequestException("Geçersiz görsel dosyası");
        }
        boolean valid = switch (contentType) {
            case "image/jpeg" -> bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8;
            case "image/png" -> bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47;
            case "image/webp" -> bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46
                    && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50;
            default -> false;
        };
        if (!valid) {
            throw new BadRequestException("Görsel dosyası geçerli bir resim değil");
        }
    }

    private String extensionForContentType(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new BadRequestException("Desteklenmeyen görsel formatı");
        };
    }

    private String readResponseBody(InputStream body) {
        if (body == null) {
            return "";
        }
        try {
            return new String(body.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return "";
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
