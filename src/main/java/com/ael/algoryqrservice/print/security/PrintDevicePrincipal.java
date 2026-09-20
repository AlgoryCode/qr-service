package com.ael.algoryqrservice.print.security;

public record PrintDevicePrincipal(Long deviceId, Long ownerUserId, Long branchId) {
}
