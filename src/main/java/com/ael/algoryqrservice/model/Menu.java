package com.ael.algoryqrservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "tbl_menu")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Menu extends QrBaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long menuId;

    @Column(nullable = false, unique = true)
    private Long qrId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String themeId;

    @Column(nullable = false)
    private String businessName;

    @Column(length = 255)
    private String slogan;

    @Column(name = "chef_name", length = 80)
    private String chefName;

    @Column(name = "chef_avatar_key", length = 64)
    private String chefAvatarKey;

    @Column(name = "logo_url", length = 1024)
    private String logoUrl;

    @Column(name = "logo_key", length = 255)
    private String logoKey;

    private String phone;
    private String email;
    private String address;

    private Long packageId;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "public_access_enabled", nullable = false)
    @lombok.Builder.Default
    private boolean publicAccessEnabled = true;

    @Column(name = "public_access_disabled_reason", length = 64)
    private String publicAccessDisabledReason;
}
