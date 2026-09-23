package com.ael.algoryqrservice.client;

import com.ael.algoryqrservice.client.dto.ExternalActivePackageResponse;

public sealed interface ActivePackageLookup permits ActivePackageLookup.Found, ActivePackageLookup.Absent {

    record Found(ExternalActivePackageResponse value) implements ActivePackageLookup {
    }

    record Absent() implements ActivePackageLookup {
    }
}
