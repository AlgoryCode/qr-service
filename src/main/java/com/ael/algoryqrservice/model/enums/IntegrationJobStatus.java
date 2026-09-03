package com.ael.algoryqrservice.model.enums;

public final class IntegrationJobStatus {

    public static final String QUEUED = "QUEUED";
    public static final String AI_PROCESSING = "AI_PROCESSING";
    public static final String BATCH_SUBMITTED = "BATCH_SUBMITTED";
    public static final String BATCH_IN_PROGRESS = "BATCH_IN_PROGRESS";
    public static final String BATCH_COMPLETED = "BATCH_COMPLETED";
    public static final String WAITING_APPROVAL = "WAITING_APPROVAL";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String PUBLISHING_INTERNAL = "PUBLISHING_INTERNAL";
    public static final String PUBLISHING_UBEREATS = "PUBLISHING_UBEREATS";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String PARTIALLY_PUBLISHED = "PARTIALLY_PUBLISHED";
    public static final String FAILED = "FAILED";

    private IntegrationJobStatus() {
    }
}
