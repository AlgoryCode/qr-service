package com.ael.algoryqrservice.paymentservice.infrastructure.messaging;

import com.ael.algoryqrservice.paymentservice.domain.exception.PaymentProcessingException;
import com.ael.algoryqrservice.paymentservice.infrastructure.config.PaymentEventsRabbitMqProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class ServiceNameValidator {

    private static final Pattern SERVICE_NAME_PATTERN = Pattern.compile("^[a-z0-9-]+$");

    private final List<String> allowedServiceNames;

    public ServiceNameValidator(PaymentEventsRabbitMqProperties properties) {
        this.allowedServiceNames = properties.allowedServiceNames();
    }

    public void validate(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            throw new PaymentProcessingException("INVALID_SERVICE_NAME", "serviceName is required");
        }
        if (!SERVICE_NAME_PATTERN.matcher(serviceName).matches()) {
            throw new PaymentProcessingException("INVALID_SERVICE_NAME", "Invalid serviceName format: " + serviceName);
        }
        if (!allowedServiceNames.isEmpty() && !allowedServiceNames.contains(serviceName)) {
            throw new PaymentProcessingException("INVALID_SERVICE_NAME", "serviceName is not allowed: " + serviceName);
        }
    }
}
