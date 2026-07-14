package com.ael.algoryqrservice.messaging.dto;

import java.util.Collections;
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
        templateData = templateData == null ? Collections.emptyMap() : Map.copyOf(templateData);
    }
}
