package com.ael.algoryqrservice.qrservice.client;

import com.ael.algoryqrservice.qrservice.client.dto.ExternalActivePackageResponse;

public sealed interface ActivePackageLookup permits ActivePackageLookup.Found, ActivePackageLookup.Absent {

    record Found(ExternalActivePackageResponse value) implements ActivePackageLookup {
    }

    record Absent() implements ActivePackageLookup {
    }
}
