package com.ael.algoryqrservice.qrservice.event;

import java.util.Set;

public record PurchasesExpiredEvent(Set<Long> merchantIds) {
}
