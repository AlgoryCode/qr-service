package com.ael.algoryqrservice.integration.ubereatsmenu;

import com.ael.algoryqrservice.model.IntegrationPendingProduct;
import com.fasterxml.jackson.databind.JsonNode;

public interface UberEatsMenuPublisher {

    PublishResult publishItem(IntegrationPendingProduct product, JsonNode payload);

    record PublishResult(boolean success, String uberItemId, String errorMessage, boolean retryable) {
        public static PublishResult ok(String uberItemId) {
            return new PublishResult(true, uberItemId, null, false);
        }

        public static PublishResult failed(String errorMessage, boolean retryable) {
            return new PublishResult(false, null, errorMessage, retryable);
        }
    }
}
