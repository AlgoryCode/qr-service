package com.ael.algoryqrservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_site_visit", indexes = {
        @Index(name = "idx_site_visit_created_at", columnList = "created_at"),
        @Index(name = "idx_site_visit_device_type", columnList = "device_type, created_at"),
        @Index(name = "idx_site_visit_country", columnList = "country_code, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class SiteVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 512)
    private String path;

    @Column(length = 1024)
    private String referrer;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(length = 128)
    private String device;

    @Column(name = "device_type", length = 20)
    private String deviceType;

    @Column(name = "country_code", length = 8)
    private String countryCode;

    @Column(name = "country_name", length = 120)
    private String countryName;

    @Column(name = "region_name", length = 120)
    private String regionName;

    @Column(length = 120)
    private String city;

    private Double latitude;

    private Double longitude;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
