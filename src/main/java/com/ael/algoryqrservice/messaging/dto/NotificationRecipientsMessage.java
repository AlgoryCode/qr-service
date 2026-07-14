package com.ael.algoryqrservice.messaging.dto;

import java.util.Collections;
import java.util.List;

public record NotificationRecipientsMessage(
        String email,
        List<String> cc,
        List<String> bcc
) {

    public NotificationRecipientsMessage {
        cc = cc == null ? Collections.emptyList() : List.copyOf(cc);
        bcc = bcc == null ? Collections.emptyList() : List.copyOf(bcc);
    }
}
