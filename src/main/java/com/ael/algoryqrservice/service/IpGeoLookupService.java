package com.ael.algoryqrservice.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Service
@Slf4j
public class IpGeoLookupService {

    private static final int TIMEOUT_MS = 2_500;

    public Optional<GeoLocation> lookup(String ipAddress) {
        if (isPrivateOrLocal(ipAddress)) {
            return Optional.empty();
        }

        try {
            URI uri = URI.create(
                    "http://ip-api.com/json/"
                            + ipAddress
                            + "?fields=status,country,countryCode,regionName,city,lat,lon"
            );
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");

            if (connection.getResponseCode() != 200) {
                return Optional.empty();
            }

            String body = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            IpApiResponse response = JsonMapper.read(body, IpApiResponse.class);
            if (response == null || !"success".equalsIgnoreCase(response.status())) {
                return Optional.empty();
            }

            return Optional.of(new GeoLocation(
                    response.countryCode(),
                    response.country(),
                    response.regionName(),
                    response.city(),
                    response.lat(),
                    response.lon()
            ));
        } catch (IOException | RuntimeException exception) {
            log.debug("IP geo lookup failed for {}: {}", ipAddress, exception.getMessage());
            return Optional.empty();
        }
    }

    static boolean isPrivateOrLocal(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return true;
        }
        String ip = ipAddress.trim();
        if ("unknown".equalsIgnoreCase(ip)) {
            return true;
        }
        if ("::1".equals(ip) || ip.startsWith("127.")) {
            return true;
        }
        if (ip.startsWith("10.")) {
            return true;
        }
        if (ip.startsWith("192.168.")) {
            return true;
        }
        if (ip.startsWith("169.254.")) {
            return true;
        }
        if (ip.startsWith("172.")) {
            String[] parts = ip.split("\\.");
            if (parts.length > 1) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    if (second >= 16 && second <= 31) {
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        return false;
    }

    public record GeoLocation(
            String countryCode,
            String countryName,
            String regionName,
            String city,
            Double latitude,
            Double longitude
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record IpApiResponse(
            String status,
            String country,
            String countryCode,
            String regionName,
            String city,
            Double lat,
            Double lon
    ) {
    }

    private static final class JsonMapper {
        private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
                new com.fasterxml.jackson.databind.ObjectMapper();

        private JsonMapper() {
        }

        private static <T> T read(String body, Class<T> type) throws IOException {
            return MAPPER.readValue(body, type);
        }
    }
}
