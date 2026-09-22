package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuThemePackSeedService {

    private final ProductImageStorageService productImageStorageService;
    private final ResourcePatternResolver resourcePatternResolver;

    public void seedIfMissing() {
        Resource[] resources;
        try {
            resources = resourcePatternResolver.getResources("classpath*:themes/**/*.*");
        } catch (IOException exception) {
            if (log.isWarnEnabled()) {
                log.warn("Theme pack classpath scan failed", exception);
            }
            return;
        }
        for (Resource resource : resources) {
            seedResource(resource);
        }
    }

    private void seedResource(Resource resource) {
        if (!resource.isReadable() || resource.getFilename() == null) {
            return;
        }
        String objectKey;
        try {
            objectKey = objectKeyFor(resource);
        } catch (IOException exception) {
            if (log.isWarnEnabled()) {
                log.warn("Theme pack path skipped: {}", resource.getFilename(), exception);
            }
            return;
        }
        if (objectKey == null) {
            return;
        }
        try {
            if (productImageStorageService.exists(objectKey)) {
                return;
            }
            byte[] bytes;
            try (InputStream inputStream = resource.getInputStream()) {
                bytes = inputStream.readAllBytes();
            }
            productImageStorageService.uploadSeedBytes(objectKey, bytes, contentTypeForPath(objectKey));
            if (log.isInfoEnabled()) {
                log.info("Seeded menu theme pack object: {}", objectKey);
            }
        } catch (Exception exception) {
            if (log.isWarnEnabled()) {
                log.warn("Theme pack seed skipped: {}", objectKey, exception);
            }
        }
    }

    String objectKeyFor(Resource resource) throws IOException {
        String uri = resource.getURI().toString().replace('\\', '/');
        int marker = uri.lastIndexOf("/themes/");
        if (marker < 0) {
            return null;
        }
        return uri.substring(marker + 1);
    }

    String contentTypeForPath(String path) {
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
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lower.endsWith(".css")) {
            return "text/css";
        }
        if (lower.endsWith(".woff2")) {
            return "font/woff2";
        }
        throw new IllegalArgumentException(path);
    }

    @Component
    @RequiredArgsConstructor
    public static class MenuThemePackSeedRunner implements ApplicationRunner {

        private final MenuThemePackSeedService menuThemePackSeedService;
        private final AppProperties appProperties;

        @Override
        public void run(ApplicationArguments args) {
            if (!appProperties.getSeed().isMenuThemes()) {
                return;
            }
            menuThemePackSeedService.seedIfMissing();
        }
    }
}
