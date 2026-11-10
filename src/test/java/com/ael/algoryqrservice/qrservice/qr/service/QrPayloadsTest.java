package com.ael.algoryqrservice.qrservice.qr.service;

import com.ael.algoryqrservice.qrservice.model.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QrPayloadsTest {

    @Test
    void mailContentUsesTheFormsMailFieldAndEncodesTheSubject() {
        Map<String, Object> details = QrPayloads.normalize(Type.MAIL,
                Map.of("mail", "test@example.com", "subject", "Merhaba Dunya & test", "body", ""));

        assertThat(QrPayloads.content(Type.MAIL, details))
                .isEqualTo("mailto:test@example.com?subject=Merhaba%20Dunya%20%26%20test");
    }

    @Test
    void contactContentCarriesTheEmailAndEscapesSeparators() {
        Map<String, Object> details = QrPayloads.normalize(Type.CONTACT,
                Map.of("fullName", "Ali; Veli", "mail", "ali@example.com", "company", "A, B"));

        assertThat(QrPayloads.content(Type.CONTACT, details))
                .contains("FN:Ali\\; Veli")
                .contains("EMAIL:ali@example.com")
                .contains("ORG:A\\, B")
                .doesNotContain("TEL:");
    }

    @Test
    void wifiContentEscapesSpecialCharacters() {
        Map<String, Object> details = QrPayloads.normalize(Type.WIFI,
                Map.of("ssid", "Cafe;1", "password", "pa:ss;w,d", "security", "WPA"));

        assertThat(QrPayloads.content(Type.WIFI, details))
                .isEqualTo("WIFI:T:WPA;S:Cafe\\;1;P:pa\\:ss\\;w\\,d;;");
    }

    @Test
    void openWifiUsesTheNopassFormat() {
        Map<String, Object> details = QrPayloads.normalize(Type.WIFI, Map.of("ssid", "Misafir", "security", "NONE"));

        assertThat(QrPayloads.content(Type.WIFI, details)).isEqualTo("WIFI:T:nopass;S:Misafir;;");
    }

    @Test
    void linkWithoutSchemeBecomesHttps() {
        assertThat(QrPayloads.normalize(Type.LINK, Map.of("url", "example.com/menu")))
                .containsEntry("url", "https://example.com/menu");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "asdasdasdasdad", "javascript:alert(1)", "ftp://example.com", "mailto:a@b.co", "http://user@example.com"})
    void invalidLinksAreRejected(String url) {
        assertThatThrownBy(() -> QrPayloads.normalize(Type.LINK, Map.of("url", url)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void emptyRequiredFieldsAreRejected() {
        assertThatThrownBy(() -> QrPayloads.normalize(Type.MAIL, Map.of("mail", "")))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> QrPayloads.normalize(Type.CONTACT, Map.of("fullName", " ")))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> QrPayloads.normalize(Type.TEXT, Map.of("text", "")))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> QrPayloads.normalize(Type.WIFI, Map.of("ssid", "x", "security", "WPA")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void coordinatesMustBeInRange() {
        assertThatThrownBy(() -> QrPayloads.normalize(Type.LOCATION, Map.of("latitude", "91", "longitude", "28")))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> QrPayloads.normalize(Type.LOCATION, Map.of("latitude", "abc", "longitude", "28")))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(QrPayloads.content(Type.LOCATION,
                QrPayloads.normalize(Type.LOCATION, Map.of("latitude", "41,0082", "longitude", "28.9784", "label", "Ana Şube"))))
                .isEqualTo("geo:41.0082,28.9784?q=41.0082,28.9784(Ana%20%C5%9Eube)");
    }

    @Test
    void locationCanBeAMapLinkAndKeepsOnlyThatLink() {
        Map<String, Object> details = QrPayloads.normalize(Type.LOCATION,
                Map.of("mapsUrl", "maps.app.goo.gl/abc123", "latitude", "41", "longitude", "28", "label", "Ana Şube"));
        assertThat(details).containsOnlyKeys("mapsUrl", "label");
        assertThat(QrPayloads.content(Type.LOCATION, details)).isEqualTo("https://maps.app.goo.gl/abc123");
        assertThatThrownBy(() -> QrPayloads.normalize(Type.LOCATION, Map.of("mapsUrl", "")))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> QrPayloads.normalize(Type.LOCATION, Map.of("mapsUrl", "javascript:alert(1)")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void modeDefaultsByTypeAndRejectsUnknownValues() {
        assertThat(QrPayloads.isDynamic(null, Type.WIFI)).isFalse();
        assertThat(QrPayloads.isDynamic(null, Type.LINK)).isTrue();
        assertThat(QrPayloads.isDynamic("dynamic", Type.WIFI)).isTrue();
        assertThat(QrPayloads.isDynamic("static", Type.LINK)).isFalse();
        assertThatThrownBy(() -> QrPayloads.isDynamic("other", Type.LINK))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void staticContentMustFitInTheImage() {
        Map<String, Object> longText = Map.of("text", "a".repeat(1001));

        assertThatThrownBy(() -> QrPayloads.requireFitsInImage(Type.TEXT, longText))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void publicCodeFormatIsStrict() {
        assertThat(QrPublicCodes.isWellFormed("k7m2xq9fah")).isTrue();
        assertThat(QrPublicCodes.isWellFormed("short")).isFalse();
        assertThat(QrPublicCodes.isWellFormed("k7m2xq9fa0")).isFalse();
        assertThat(QrPublicCodes.isWellFormed(null)).isFalse();
    }
}
