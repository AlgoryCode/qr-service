package com.ael.algoryqrservice.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IpGeoLookupServiceTest {

    @Test
    void isPrivateOrLocal_detectsLoopbackAndPrivateRanges() {
        assertThat(IpGeoLookupService.isPrivateOrLocal(null)).isTrue();
        assertThat(IpGeoLookupService.isPrivateOrLocal("")).isTrue();
        assertThat(IpGeoLookupService.isPrivateOrLocal("unknown")).isTrue();
        assertThat(IpGeoLookupService.isPrivateOrLocal("127.0.0.1")).isTrue();
        assertThat(IpGeoLookupService.isPrivateOrLocal("::1")).isTrue();
        assertThat(IpGeoLookupService.isPrivateOrLocal("10.0.0.5")).isTrue();
        assertThat(IpGeoLookupService.isPrivateOrLocal("192.168.1.10")).isTrue();
        assertThat(IpGeoLookupService.isPrivateOrLocal("172.16.0.1")).isTrue();
        assertThat(IpGeoLookupService.isPrivateOrLocal("172.31.255.255")).isTrue();
    }

    @Test
    void isPrivateOrLocal_allowsPublicIp() {
        assertThat(IpGeoLookupService.isPrivateOrLocal("8.8.8.8")).isFalse();
        assertThat(IpGeoLookupService.isPrivateOrLocal("1.1.1.1")).isFalse();
        assertThat(IpGeoLookupService.isPrivateOrLocal("172.15.0.1")).isFalse();
    }

    @Test
    void lookup_skipsPrivateIp() {
        IpGeoLookupService service = new IpGeoLookupService();
        assertThat(service.lookup("127.0.0.1")).isEmpty();
        assertThat(service.lookup("192.168.0.1")).isEmpty();
    }
}
