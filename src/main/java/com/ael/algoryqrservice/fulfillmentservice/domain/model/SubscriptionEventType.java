package com.ael.algoryqrservice.fulfillmentservice.domain.model;

/** Every change in a merchant's subscription that is recorded in history and may trigger a mail. */
public enum SubscriptionEventType {
    TRIAL_STARTED,
    /** Reminder before the trial's last day; recorded once per reminder day. */
    TRIAL_ENDING,
    TRIAL_ENDED,
    PACKAGE_ACTIVATED,
    PACKAGE_RENEWED,
    PACKAGE_CHANGED,
    PAYMENT_FAILED,
    PAYMENT_RECOVERED,
    PACKAGE_REFUNDED,
    DOWNGRADED_TO_FREE,
    ADDON_GRANTED,
    ADDON_REVOKED
}
