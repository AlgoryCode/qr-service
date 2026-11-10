package com.ael.algoryqrservice.qrservice.qr.service;

import com.ael.algoryqrservice.qrservice.model.Type;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Validates QR details per type and turns them into the text a phone understands (mailto:, vCard, WIFI:, geo:). */
public final class QrPayloads {

    private static final int MAX_NAME_LENGTH = 120;
    private static final int MAX_URL_LENGTH = 2048;
    private static final int MAX_TEXT_LENGTH = 4000;
    private static final int MAX_STATIC_BYTES = 1000;
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Set<String> WIFI_SECURITY = Set.of("WPA", "WEP", "NONE");

    private QrPayloads() {
    }

    public static String requireName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw badRequest("QR kod adı zorunludur.");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw badRequest("QR kod adı en fazla " + MAX_NAME_LENGTH + " karakter olabilir.");
        }
        return trimmed;
    }

    /** A WiFi QR defaults to static because a phone only joins a network automatically from the image itself. */
    public static boolean isDynamic(String mode, Type type) {
        if (mode == null || mode.isBlank()) {
            return type != Type.WIFI;
        }
        return switch (mode.trim().toLowerCase(Locale.ROOT)) {
            case "dynamic" -> true;
            case "static" -> false;
            default -> throw badRequest("Geçersiz QR modu.");
        };
    }

    /** Longer content cannot be drawn densely enough to scan reliably in the generated image size. */
    public static void requireFitsInImage(Type type, Map<String, Object> details) {
        if (content(type, details).getBytes(StandardCharsets.UTF_8).length > MAX_STATIC_BYTES) {
            throw badRequest("İçerik sabit QR için çok uzun. Dinamik QR seçin veya içeriği kısaltın.");
        }
    }

    /** Returns only the fields the type uses, cleaned up; rejects details that would produce a useless QR. */
    public static Map<String, Object> normalize(Type type, Map<String, Object> details) {
        if (details == null) {
            throw badRequest("QR içeriği zorunludur.");
        }
        return switch (type) {
            case LINK -> normalizeLink(details);
            case MAIL -> normalizeMail(details);
            case CONTACT -> normalizeContact(details);
            case TEXT -> normalizeText(details);
            case LOCATION -> normalizeLocation(details);
            case WIFI -> normalizeWifi(details);
            case MENU -> throw new IllegalArgumentException("Menu QR details are handled by MenuService");
        };
    }

    public static String content(Type type, Map<String, Object> details) {
        return switch (type) {
            case LINK -> text(details, "url");
            case MAIL -> mailContent(details);
            case CONTACT -> contactContent(details);
            case TEXT -> text(details, "text");
            case LOCATION -> locationContent(details);
            case WIFI -> wifiContent(details);
            case MENU -> throw new IllegalArgumentException("Menu QR content is built by MenuService");
        };
    }

    /** Non-menu rows keep no type column, so the type follows from which fields their details hold. */
    public static Type inferType(JsonNode details) {
        if (details == null) {
            return null;
        }
        if (details.has("url")) return Type.LINK;
        if (details.has("ssid")) return Type.WIFI;
        if (details.has("fullName")) return Type.CONTACT;
        if (details.has("mail") || details.has("email")) return Type.MAIL;
        if (details.has("latitude") || details.has("mapsUrl")) return Type.LOCATION;
        if (details.has("text")) return Type.TEXT;
        return null;
    }

    private static Map<String, Object> normalizeLink(Map<String, Object> details) {
        String url = text(details, "url");
        if (url.isEmpty()) {
            throw badRequest("URL zorunludur.");
        }
        if (url.length() > MAX_URL_LENGTH) {
            throw badRequest("URL çok uzun.");
        }
        String absolute = url.contains("://") ? url : "https://" + url;
        if (!isWebUrl(absolute)) {
            throw badRequest("Geçerli bir web adresi girin. Örn: https://example.com");
        }
        return Map.of("url", absolute);
    }

    private static boolean isWebUrl(String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            String authority = uri.getRawAuthority();
            if (scheme == null || authority == null || authority.contains("@")) {
                return false;
            }
            if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
                return false;
            }
            String host = IDN.toASCII(authority.replaceFirst(":\\d+$", ""));
            return host.equalsIgnoreCase("localhost")
                    || (host.contains(".") && !host.startsWith(".") && !host.endsWith("."));
        } catch (URISyntaxException | IllegalArgumentException ex) {
            return false;
        }
    }

    private static Map<String, Object> normalizeMail(Map<String, Object> details) {
        String mail = mailAddress(details);
        if (mail.isEmpty()) {
            throw badRequest("E-posta adresi zorunludur.");
        }
        requireEmailFormat(mail);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mail", mail);
        result.put("subject", text(details, "subject"));
        result.put("body", text(details, "body"));
        return result;
    }

    private static Map<String, Object> normalizeContact(Map<String, Object> details) {
        String fullName = text(details, "fullName");
        if (fullName.isEmpty()) {
            throw badRequest("Ad Soyad zorunludur.");
        }
        String mail = mailAddress(details);
        if (!mail.isEmpty()) {
            requireEmailFormat(mail);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fullName", fullName);
        result.put("phone", text(details, "phone"));
        result.put("mail", mail);
        result.put("company", text(details, "company"));
        result.put("title", text(details, "title"));
        return result;
    }

    private static Map<String, Object> normalizeText(Map<String, Object> details) {
        String text = text(details, "text");
        if (text.isEmpty()) {
            throw badRequest("Metin zorunludur.");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw badRequest("Metin en fazla " + MAX_TEXT_LENGTH + " karakter olabilir.");
        }
        return Map.of("text", text);
    }

    /**
     * A location is either coordinates or a map link (for example a Google Maps business profile).
     * Only the chosen one is kept, so the stored details never mix the two.
     */
    private static Map<String, Object> normalizeLocation(Map<String, Object> details) {
        if (details.containsKey("mapsUrl")) {
            String url = text(details, "mapsUrl");
            if (url.isEmpty()) {
                throw badRequest("Google Maps bağlantısı zorunludur.");
            }
            if (url.length() > MAX_URL_LENGTH) {
                throw badRequest("Harita bağlantısı çok uzun.");
            }
            String absolute = url.contains("://") ? url : "https://" + url;
            if (!isWebUrl(absolute)) {
                throw badRequest("Geçerli bir harita bağlantısı girin. Örn: https://maps.app.goo.gl/...");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("mapsUrl", absolute);
            result.put("label", text(details, "label"));
            return result;
        }
        String latitude = text(details, "latitude").replace(',', '.');
        String longitude = text(details, "longitude").replace(',', '.');
        requireCoordinate(latitude, 90, "Enlem -90 ile 90 arasında olmalıdır.");
        requireCoordinate(longitude, 180, "Boylam -180 ile 180 arasında olmalıdır.");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("latitude", latitude);
        result.put("longitude", longitude);
        result.put("label", text(details, "label"));
        return result;
    }

    private static void requireCoordinate(String value, int limit, String message) {
        try {
            if (new BigDecimal(value).abs().compareTo(BigDecimal.valueOf(limit)) > 0) {
                throw badRequest(message);
            }
        } catch (NumberFormatException ex) {
            throw badRequest(message);
        }
    }

    private static Map<String, Object> normalizeWifi(Map<String, Object> details) {
        String ssid = text(details, "ssid");
        if (ssid.isEmpty()) {
            throw badRequest("WiFi ağ adı (SSID) zorunludur.");
        }
        String security = text(details, "security").toUpperCase(Locale.ROOT);
        if (security.isEmpty()) {
            security = "WPA";
        }
        if (!WIFI_SECURITY.contains(security)) {
            throw badRequest("Geçersiz WiFi şifreleme türü.");
        }
        // A WiFi password can legitimately start or end with a space, so it is not trimmed.
        Object rawPassword = details.get("password");
        String password = rawPassword == null ? "" : rawPassword.toString();
        if (!security.equals("NONE") && password.isEmpty()) {
            throw badRequest("WiFi şifresi zorunludur.");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ssid", ssid);
        result.put("password", security.equals("NONE") ? "" : password);
        result.put("security", security);
        return result;
    }

    private static String mailContent(Map<String, Object> details) {
        List<String> query = new ArrayList<>();
        String subject = text(details, "subject");
        String body = text(details, "body");
        if (!subject.isEmpty()) {
            query.add("subject=" + percentEncode(subject));
        }
        if (!body.isEmpty()) {
            query.add("body=" + percentEncode(body));
        }
        String address = "mailto:" + mailAddress(details);
        return query.isEmpty() ? address : address + "?" + String.join("&", query);
    }

    private static String contactContent(Map<String, Object> details) {
        List<String> lines = new ArrayList<>();
        lines.add("BEGIN:VCARD");
        lines.add("VERSION:3.0");
        lines.add("FN:" + escapeVcard(text(details, "fullName")));
        addVcardLine(lines, "TEL", text(details, "phone"));
        addVcardLine(lines, "EMAIL", mailAddress(details));
        addVcardLine(lines, "ORG", text(details, "company"));
        addVcardLine(lines, "TITLE", text(details, "title"));
        lines.add("END:VCARD");
        return String.join("\r\n", lines);
    }

    private static void addVcardLine(List<String> lines, String key, String value) {
        if (!value.isEmpty()) {
            lines.add(key + ":" + escapeVcard(value));
        }
    }

    private static String escapeVcard(String value) {
        return value.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n");
    }

    private static String locationContent(Map<String, Object> details) {
        String mapsUrl = text(details, "mapsUrl");
        if (!mapsUrl.isEmpty()) {
            return mapsUrl;
        }
        String coordinates = text(details, "latitude") + "," + text(details, "longitude");
        String label = text(details, "label");
        if (label.isEmpty()) {
            return "geo:" + coordinates;
        }
        return "geo:" + coordinates + "?q=" + coordinates + "(" + percentEncode(label) + ")";
    }

    private static String wifiContent(Map<String, Object> details) {
        String ssid = escapeWifi(text(details, "ssid"));
        String security = text(details, "security");
        if (security.equals("NONE")) {
            return "WIFI:T:nopass;S:" + ssid + ";;";
        }
        Object password = details.get("password");
        return "WIFI:T:" + security + ";S:" + ssid + ";P:" + escapeWifi(password == null ? "" : password.toString()) + ";;";
    }

    private static String escapeWifi(String value) {
        return value.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace(":", "\\:")
                .replace("\"", "\\\"");
    }

    private static String percentEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String mailAddress(Map<String, Object> details) {
        String mail = text(details, "mail");
        return mail.isEmpty() ? text(details, "email") : mail;
    }

    private static void requireEmailFormat(String mail) {
        if (!EMAIL.matcher(mail).matches()) {
            throw badRequest("Geçerli bir e-posta adresi girin.");
        }
    }

    private static String text(Map<String, Object> details, String key) {
        Object value = details.get(key);
        return value == null ? "" : value.toString().trim();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
