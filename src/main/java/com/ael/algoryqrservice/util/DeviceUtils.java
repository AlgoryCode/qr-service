package com.ael.algoryqrservice.util;

public final class DeviceUtils {

    private DeviceUtils() {
    }

    public static String resolveDeviceType(String userAgent) {
        if (userAgent == null || userAgent.isBlank() || "unknown".equalsIgnoreCase(userAgent)) {
            return "UNKNOWN";
        }

        String ua = userAgent.toLowerCase();

        if (ua.contains("ipad") || ua.contains("tablet") || ua.contains("kindle")) {
            return "TABLET";
        }
        if (ua.contains("mobile")
                || ua.contains("iphone")
                || ua.contains("android")
                || ua.contains("ipod")
                || ua.contains("phone")) {
            return "MOBILE";
        }
        if (ua.contains("windows")
                || ua.contains("macintosh")
                || ua.contains("mac os")
                || ua.contains("linux")
                || ua.contains("cros")) {
            return "DESKTOP";
        }

        return "UNKNOWN";
    }

    public static String resolveDeviceName(String userAgent) {
        if (userAgent == null || userAgent.isBlank() || "unknown".equalsIgnoreCase(userAgent)) {
            return "Bilinmeyen cihaz";
        }

        return resolveBrowser(userAgent) + " / " + resolveOperatingSystem(userAgent);
    }

    private static String resolveBrowser(String userAgent) {
        String ua = userAgent;

        if (ua.contains("Edg/")) {
            return "Microsoft Edge";
        }
        if (ua.contains("OPR/") || ua.contains("Opera")) {
            return "Opera";
        }
        if (ua.contains("Chrome/") && !ua.contains("Edg/")) {
            return "Chrome";
        }
        if (ua.contains("Safari/") && !ua.contains("Chrome/")) {
            return "Safari";
        }
        if (ua.contains("Firefox/")) {
            return "Firefox";
        }
        if (ua.contains("MSIE") || ua.contains("Trident/")) {
            return "Internet Explorer";
        }

        return "Bilinmeyen tarayıcı";
    }

    private static String resolveOperatingSystem(String userAgent) {
        String ua = userAgent;

        if (ua.contains("iPhone")) {
            return "iPhone";
        }
        if (ua.contains("iPad")) {
            return "iPad";
        }
        if (ua.contains("Android")) {
            return "Android";
        }
        if (ua.contains("Windows NT 10.0")) {
            return "Windows";
        }
        if (ua.contains("Windows")) {
            return "Windows";
        }
        if (ua.contains("Mac OS X") || ua.contains("Macintosh")) {
            return "macOS";
        }
        if (ua.contains("Linux")) {
            return "Linux";
        }

        return "Bilinmeyen işletim sistemi";
    }
}
