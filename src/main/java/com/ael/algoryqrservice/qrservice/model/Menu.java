package com.ael.algoryqrservice.qrservice.model;

import com.ael.algoryqrservice.qrservice.model.enums.MenuChannel;
import com.ael.algoryqrservice.qrservice.model.enums.MenuPublicAccessDisabledReason;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

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

    @Column(unique = true)
    private Long qrId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private MenuChannel channel = MenuChannel.QR;

    @Column(name = "public_id", nullable = false, unique = true, length = 32)
    private String publicId;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "branch_id")
    private Long branchId;

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

    @Column(name = "cover_url", length = 1024)
    private String coverUrl;

    @Column(name = "cover_key", length = 255)
    private String coverKey;

    private String phone;
    private String email;
    private String address;

    @Column(name = "google_reviews_url", length = 1024)
    private String googleReviewsUrl;

    @Column(name = "facebook_url", length = 512)
    private String facebookUrl;

    @Column(name = "instagram_url", length = 512)
    private String instagramUrl;

    @Column(name = "youtube_url", length = 512)
    private String youtubeUrl;

    @Column(name = "tiktok_url", length = 512)
    private String tiktokUrl;

    @Column(name = "x_url", length = 512)
    private String xUrl;

    @Column(name = "show_facebook", nullable = false)
    @Builder.Default
    private boolean showFacebook = true;

    @Column(name = "show_instagram", nullable = false)
    @Builder.Default
    private boolean showInstagram = true;

    @Column(name = "show_youtube", nullable = false)
    @Builder.Default
    private boolean showYoutube = true;

    @Column(name = "show_tiktok", nullable = false)
    @Builder.Default
    private boolean showTiktok = true;

    @Column(name = "show_x", nullable = false)
    @Builder.Default
    private boolean showX = true;

    private Long packageId;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "public_access_enabled", nullable = false)
    @lombok.Builder.Default
    private boolean publicAccessEnabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "public_access_disabled_reason", length = 64)
    private MenuPublicAccessDisabledReason publicAccessDisabledReason;

    @Column(name = "rating_avg", nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    @Column(name = "rating_count", nullable = false)
    @Builder.Default
    private long ratingCount = 0L;
}
