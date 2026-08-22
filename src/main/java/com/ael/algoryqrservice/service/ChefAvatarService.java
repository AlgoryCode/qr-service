package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.config.ChefAvatarProperties;
import com.ael.algoryqrservice.model.dto.MenuDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChefAvatarService {

    public static final String DEFAULT_CHEF_NAME = "Akıllı Şef";
    public static final String DEFAULT_AVATAR_KEY = "default";

    private final ChefAvatarProperties chefAvatarProperties;
    private final ProductImageStorageService productImageStorageService;

    public List<MenuDtos.ChefAvatarItem> listAvatars() {
        return chefAvatarProperties.getItems().stream()
                .filter(this::isConfiguredItem)
                .map(item -> MenuDtos.ChefAvatarItem.builder()
                        .key(item.getKey().trim())
                        .label(item.getLabel() == null || item.getLabel().isBlank()
                                ? item.getKey().trim()
                                : item.getLabel().trim())
                        .imageUrl(productImageStorageService.buildPublicUrl(item.getObjectKey().trim()))
                        .build())
                .toList();
    }

    public boolean isValidKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String normalized = key.trim();
        return chefAvatarProperties.getItems().stream()
                .filter(this::isConfiguredItem)
                .anyMatch(item -> normalized.equals(item.getKey().trim()));
    }

    public Optional<ChefAvatarProperties.Item> findItem(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String normalized = key.trim();
        return chefAvatarProperties.getItems().stream()
                .filter(this::isConfiguredItem)
                .filter(item -> normalized.equals(item.getKey().trim()))
                .findFirst();
    }

    public String resolveImageUrl(String avatarKey) {
        Optional<ChefAvatarProperties.Item> item = findItem(avatarKey);
        if (item.isEmpty()) {
            item = findItem(DEFAULT_AVATAR_KEY);
        }
        return item.map(value -> productImageStorageService.buildPublicUrl(value.getObjectKey().trim()))
                .orElse(null);
    }

    public String resolveDisplayName(String chefName) {
        if (chefName == null || chefName.isBlank()) {
            return DEFAULT_CHEF_NAME;
        }
        return chefName.trim();
    }

    public void seedCatalogIfMissing() {
        for (ChefAvatarProperties.Item item : chefAvatarProperties.getItems()) {
            if (!isConfiguredItem(item)) {
                continue;
            }
            String objectKey = item.getObjectKey().trim();
            try {
                if (productImageStorageService.exists(objectKey)) {
                    continue;
                }
                String resourcePath = item.getClasspathResource() == null || item.getClasspathResource().isBlank()
                        ? null
                        : item.getClasspathResource().trim();
                if (resourcePath == null) {
                    if (log.isWarnEnabled()) {
                        log.warn("Chef avatar missing in storage and no classpath resource: key={}", item.getKey());
                    }
                    continue;
                }
                ClassPathResource resource = new ClassPathResource(resourcePath);
                if (!resource.exists()) {
                    if (log.isWarnEnabled()) {
                        log.warn("Chef avatar classpath resource not found: {}", resourcePath);
                    }
                    continue;
                }
                byte[] bytes;
                try (InputStream inputStream = resource.getInputStream()) {
                    bytes = inputStream.readAllBytes();
                }
                String contentType = contentTypeForPath(resourcePath);
                productImageStorageService.uploadBytes(objectKey, bytes, contentType);
                if (log.isInfoEnabled()) {
                    log.info("Seeded chef avatar: key={} objectKey={}", item.getKey(), objectKey);
                }
            } catch (Exception exception) {
                if (log.isWarnEnabled()) {
                    log.warn("Chef avatar seed skipped: key={} objectKey={}", item.getKey(), objectKey, exception);
                }
            }
        }
    }

    private boolean isConfiguredItem(ChefAvatarProperties.Item item) {
        return item != null
                && item.getKey() != null && !item.getKey().isBlank()
                && item.getObjectKey() != null && !item.getObjectKey().isBlank();
    }

    private String contentTypeForPath(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/png";
    }

    @Component
    @RequiredArgsConstructor
    public static class ChefAvatarSeedRunner implements ApplicationRunner {

        private final ChefAvatarService chefAvatarService;
        private final AppProperties appProperties;

        @Override
        public void run(ApplicationArguments args) {
            if (!appProperties.getSeed().isChefAvatars()) {
                return;
            }
            chefAvatarService.seedCatalogIfMissing();
        }
    }
}
