package com.ael.algoryqrservice.qrservice.analytics.assistant;

public enum AssistantUsageOutcome {
    /** The assistant replied, so the merchant got the answer without opening a report. */
    ANSWERED,
    /** The assistant could not reply (timeout, upstream error). */
    FAILED
}
