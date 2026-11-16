package com.ael.algoryqrservice.fulfillmentservice.domain.model;

public enum EntitlementSource {
    /** A line of the plan package itself; rotates with every package renewal. */
    PACKAGE_INCLUDE,
    /** A module the buyer added to the package at checkout; rotates with the package like its own lines. */
    PACKAGE_MODULE,
    /** A separately bought add-on (extra branch, report credits); outlives package renewals. */
    ADDON_PURCHASE,
    ONBOARDING_PACKAGE
}
