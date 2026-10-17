package com.ael.algoryqrservice.qrservice.client;

import com.ael.algoryqrservice.qrservice.client.dto.ExternalActivePackageResponse;

public record PackageView(String status, ExternalActivePackageResponse body) {
}
