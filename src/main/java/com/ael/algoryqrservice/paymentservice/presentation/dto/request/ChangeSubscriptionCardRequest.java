package com.ael.algoryqrservice.paymentservice.presentation.dto.request;

import jakarta.validation.constraints.NotNull;

public record ChangeSubscriptionCardRequest(@NotNull Long paymentMethodId) {
}
