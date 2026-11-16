package com.ael.algoryqrservice.fulfillmentservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * @param trialDays        length of the free full trial every new merchant gets
 * @param graceDays        days a paid package stays open after its renewal payment fails
 * @param trialReminderDaysBefore days before the trial's last day on which a reminder mail goes out
 */
@ConfigurationProperties(prefix = "billing.lifecycle")
public record BillingLifecycleProperties(
        Integer trialDays,
        Integer graceDays,
        List<Integer> trialReminderDaysBefore
) {

    public BillingLifecycleProperties {
        trialDays = trialDays == null || trialDays < 1 ? 14 : trialDays;
        graceDays = graceDays == null || graceDays < 0 ? 7 : graceDays;
        trialReminderDaysBefore = trialReminderDaysBefore == null || trialReminderDaysBefore.isEmpty()
                ? List.of(7, 2, 0)
                : List.copyOf(trialReminderDaysBefore);
    }
}
