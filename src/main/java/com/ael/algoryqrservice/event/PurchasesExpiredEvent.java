package com.ael.algoryqrservice.event;

import java.util.Set;

/**
 * Raised after one or more purchases were moved to EXPIRED, so downstream state
 * (subscription selection, public menu access) can be recomputed.
 */
public record PurchasesExpiredEvent(Set<Long> userIds) {
}
