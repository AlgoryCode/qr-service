package com.ael.algoryqrservice.client;

import com.ael.algoryqrservice.config.FulfillmentExternalProperties;
import com.ael.algoryqrservice.exception.FulfillmentQuotaExceededException;
import com.ael.algoryqrservice.exception.FulfillmentUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FulfillmentServiceClientTest {

    private static final String ACTIVE_PACKAGE_URL = "http://fulfillment.test/api/v1/users/7/active-package";
    private static final String ENTITLEMENTS_URL = "http://fulfillment.test/api/v1/users/7/entitlements";

    private MockRestServiceServer server;
    private FulfillmentServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        FulfillmentExternalProperties properties = new FulfillmentExternalProperties();
        properties.setBaseUrl("http://fulfillment.test");
        properties.setAuthHeader("X-Service-Token");
        properties.setAuthToken("token");
        client = new FulfillmentServiceClient(builder, properties);
    }

    @Test
    void lookupActivePackage_whenMissing_thenAbsent() {
        server.expect(requestTo(ACTIVE_PACKAGE_URL))
                .andExpect(header("X-Service-Token", "token"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.lookupActivePackage(7L)).isInstanceOf(ActivePackageLookup.Absent.class);
        server.verify();
    }

    @Test
    void lookupActivePackage_whenServerError_thenUnavailable() {
        server.expect(requestTo(ACTIVE_PACKAGE_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.lookupActivePackage(7L))
                .isInstanceOf(FulfillmentUnavailableException.class);
    }

    @Test
    void findActivePackage_whenServerError_thenEmpty() {
        server.expect(requestTo(ACTIVE_PACKAGE_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThat(client.findActivePackage(7L)).isEmpty();
    }

    @Test
    void listEntitlements_whenOk_thenReturnRights() {
        server.expect(requestTo(ENTITLEMENTS_URL))
                .andRespond(withSuccess("""
                        [{"id":9,"userId":7,"featureCode":"QR_MENU","scopeCode":"QR_MENU_OWNER","quantity":10,"unlimited":false,"usedQuantity":2,"source":"PACKAGE_INCLUDE"}]
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.listEntitlements(7L)).singleElement().satisfies(item -> {
            assertThat(item.featureCode()).isEqualTo("QR_MENU");
            assertThat(item.quantity()).isEqualTo(10);
            assertThat(item.usedQuantity()).isEqualTo(2);
        });
        server.verify();
    }

    @Test
    void findProductAccess_whenAllowed_thenReturnPackage() {
        server.expect(requestTo("http://fulfillment.test/api/v1/users/7/products/QR_CREATE"))
                .andRespond(withSuccess("""
                        {"allowed":true,"packageCode":"ULTIMATE_PACKAGE","featureCode":"QR_CREATE","scopeCode":"QR_CREATE_OWNER"}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.findProductAccess(7L, "QR_CREATE").allowed()).isTrue();
        server.verify();
    }

    @Test
    void consume_whenConflict_thenQuotaExceeded() {
        server.expect(requestTo("http://fulfillment.test/api/v1/users/7/entitlements/consume"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> client.consume(7L, "QR_CREATE", 1))
                .isInstanceOf(FulfillmentQuotaExceededException.class);
    }
}
