package com.ael.algoryqrservice.util;

import jakarta.servlet.http.HttpServletRequest;

public record ClientInfo(
        String ipAddress,
        String userAgent,
        String device,
        String deviceType
) {

    public static ClientInfo from(HttpServletRequest request) {
        String ipAddress = RequestUtils.resolveClientIp(request);
        String userAgent = RequestUtils.resolveUserAgent(request);
        return new ClientInfo(
                ipAddress,
                userAgent,
                DeviceUtils.resolveDeviceName(userAgent),
                DeviceUtils.resolveDeviceType(userAgent)
        );
    }
}
