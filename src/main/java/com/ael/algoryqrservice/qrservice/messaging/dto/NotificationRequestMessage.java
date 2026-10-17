package com.ael.algoryqrservice.qrservice.messaging.dto;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record NotificationRequestMessage(
        UUID eventId,
        List<String> channels,
        String serviceName,
        String messageType,
        NotificationRecipientsMessage recipients,
        String subject,
        Map<String, Object> templateData,
        Boolean kvkkApproved
) {

    public NotificationRequestMessage {
        channels = channels == null ? List.of() : List.copyOf(channels);
        templateData = copyTemplateData(templateData);
    }

    private static Map<String, Object> copyTemplateData(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> copy = HashMap.newHashMap(source.size());
        source.forEach((key, value) -> {
            if (key != null) {
                copy.put(key, value == null ? "" : value);
            }
        });
        return Map.copyOf(copy);
    }
}
