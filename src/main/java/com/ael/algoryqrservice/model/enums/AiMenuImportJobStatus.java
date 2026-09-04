package com.ael.algoryqrservice.model.enums;

public final class AiMenuImportJobStatus {

    public static final String QUEUED = "QUEUED";
    public static final String EXTRACTING = "EXTRACTING";
    public static final String BATCH_SUBMITTED = "BATCH_SUBMITTED";
    public static final String BATCH_IN_PROGRESS = "BATCH_IN_PROGRESS";
    public static final String WAITING_APPROVAL = "WAITING_APPROVAL";
    public static final String FAILED = "FAILED";

    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    private AiMenuImportJobStatus() {
    }
}
