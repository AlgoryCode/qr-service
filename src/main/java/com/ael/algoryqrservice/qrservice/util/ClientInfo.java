package com.ael.algoryqrservice.qrservice.util;

import jakarta.servlet.http.HttpServletRequest;
import com.ael.algoryqrservice.authservice.util.DeviceUtils;
import com.ael.algoryqrservice.authservice.util.RequestUtils;

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
