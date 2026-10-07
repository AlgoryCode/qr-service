package com.ael.algoryqrservice.client;

import com.ael.algoryqrservice.client.dto.ExternalActivePackageResponse;

public record PackageView(String status, ExternalActivePackageResponse body) {
}
