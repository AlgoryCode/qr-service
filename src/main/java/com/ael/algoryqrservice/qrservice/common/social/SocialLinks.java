package com.ael.algoryqrservice.qrservice.common.social;

import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.model.Branch;
import com.ael.algoryqrservice.qrservice.model.Menu;

import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Normalises, stores and resolves the social media links of branches and menus. */
public final class SocialLinks {

    private static final int MAX_LENGTH = 512;
    private static final Pattern HANDLE = Pattern.compile("@?[A-Za-z0-9._-]{1,100}");

    private SocialLinks() {
    }

    public enum Platform {
        FACEBOOK("Facebook", "https://www.facebook.com/", false, "facebook.com", "fb.com", "fb.me"),
        INSTAGRAM("Instagram", "https://www.instagram.com/", false, "instagram.com", "instagr.am"),
        YOUTUBE("YouTube", "https://www.youtube.com/", true, "youtube.com", "youtu.be"),
        TIKTOK("TikTok", "https://www.tiktok.com/", true, "tiktok.com"),
        X("X", "https://x.com/", false, "x.com", "twitter.com");

        private final String label;
        private final String handleBase;
        private final boolean handleNeedsAt;
        private final String[] domains;

        Platform(String label, String handleBase, boolean handleNeedsAt, String... domains) {
            this.label = label;
            this.handleBase = handleBase;
            this.handleNeedsAt = handleNeedsAt;
            this.domains = domains;
        }
    }

    /**
     * Turns user input into a link: "" or blank clears it, a bare handle ("@tavsan") becomes the
     * platform's profile URL, a domain path gets https://, and a full URL must point at the platform.
     */
    public static String normalize(Platform platform, String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        String url;
        if (value.matches("(?i)https?://.+")) {
            url = value;
        } else if (value.matches("(?i)(www\\.|m\\.)?[a-z0-9.-]+\\.[a-z]{2,}(/.*)?") && hostMatches(platform, value)) {
            url = "https://" + value;
        } else if (HANDLE.matcher(value).matches()) {
            String handle = value.startsWith("@") ? value.substring(1) : value;
            url = platform.handleBase + (platform.handleNeedsAt ? "@" : "") + handle;
        } else {
            throw new BadRequestException(platform.label + " linki geçersiz");
        }
        if (!hostMatches(platform, url.replaceFirst("(?i)^https?://", ""))) {
            throw new BadRequestException(platform.label + " linki " + platform.domains[0] + " adresine ait olmalı");
        }
        if (url.length() > MAX_LENGTH) {
            throw new BadRequestException(platform.label + " linki çok uzun");
        }
        return url;
    }

    private static boolean hostMatches(Platform platform, String withoutScheme) {
        String host = withoutScheme.split("[/?#]", 2)[0].toLowerCase(Locale.ROOT);
        for (String domain : platform.domains) {
            if (host.equals(domain) || host.endsWith("." + domain)) {
                return true;
            }
        }
        return false;
    }

    // ---- Branch ----

    public static void apply(Branch branch, SocialDtos.Links links) {
        if (links == null) {
            return;
        }
        if (links.getFacebook() != null) branch.setFacebookUrl(normalize(Platform.FACEBOOK, links.getFacebook()));
        if (links.getInstagram() != null) branch.setInstagramUrl(normalize(Platform.INSTAGRAM, links.getInstagram()));
        if (links.getYoutube() != null) branch.setYoutubeUrl(normalize(Platform.YOUTUBE, links.getYoutube()));
        if (links.getTiktok() != null) branch.setTiktokUrl(normalize(Platform.TIKTOK, links.getTiktok()));
        if (links.getX() != null) branch.setXUrl(normalize(Platform.X, links.getX()));
    }

    public static SocialDtos.Links of(Branch branch) {
        if (branch == null) {
            return SocialDtos.Links.builder().build();
        }
        return SocialDtos.Links.builder()
                .facebook(branch.getFacebookUrl())
                .instagram(branch.getInstagramUrl())
                .youtube(branch.getYoutubeUrl())
                .tiktok(branch.getTiktokUrl())
                .x(branch.getXUrl())
                .build();
    }

    // ---- Menu ----

    public static void apply(Menu menu, SocialDtos.Links links) {
        if (links == null) {
            return;
        }
        if (links.getFacebook() != null) menu.setFacebookUrl(normalize(Platform.FACEBOOK, links.getFacebook()));
        if (links.getInstagram() != null) menu.setInstagramUrl(normalize(Platform.INSTAGRAM, links.getInstagram()));
        if (links.getYoutube() != null) menu.setYoutubeUrl(normalize(Platform.YOUTUBE, links.getYoutube()));
        if (links.getTiktok() != null) menu.setTiktokUrl(normalize(Platform.TIKTOK, links.getTiktok()));
        if (links.getX() != null) menu.setXUrl(normalize(Platform.X, links.getX()));
    }

    public static void apply(Menu menu, SocialDtos.Visibility visibility) {
        if (visibility == null) {
            return;
        }
        if (visibility.getFacebook() != null) menu.setShowFacebook(visibility.getFacebook());
        if (visibility.getInstagram() != null) menu.setShowInstagram(visibility.getInstagram());
        if (visibility.getYoutube() != null) menu.setShowYoutube(visibility.getYoutube());
        if (visibility.getTiktok() != null) menu.setShowTiktok(visibility.getTiktok());
        if (visibility.getX() != null) menu.setShowX(visibility.getX());
    }

    public static SocialDtos.Links of(Menu menu) {
        return SocialDtos.Links.builder()
                .facebook(menu.getFacebookUrl())
                .instagram(menu.getInstagramUrl())
                .youtube(menu.getYoutubeUrl())
                .tiktok(menu.getTiktokUrl())
                .x(menu.getXUrl())
                .build();
    }

    public static SocialDtos.Visibility visibilityOf(Menu menu) {
        return SocialDtos.Visibility.builder()
                .facebook(menu.isShowFacebook())
                .instagram(menu.isShowInstagram())
                .youtube(menu.isShowYoutube())
                .tiktok(menu.isShowTiktok())
                .x(menu.isShowX())
                .build();
    }

    /**
     * The links the public menu shows: the branch's link wins over the menu's own, and a
     * platform the menu has switched off is left out (null).
     */
    public static SocialDtos.Links publicLinks(Menu menu, Branch branch) {
        SocialDtos.Links own = of(menu);
        SocialDtos.Links fromBranch = of(branch);
        return SocialDtos.Links.builder()
                .facebook(pick(menu.isShowFacebook(), fromBranch, own, SocialDtos.Links::getFacebook))
                .instagram(pick(menu.isShowInstagram(), fromBranch, own, SocialDtos.Links::getInstagram))
                .youtube(pick(menu.isShowYoutube(), fromBranch, own, SocialDtos.Links::getYoutube))
                .tiktok(pick(menu.isShowTiktok(), fromBranch, own, SocialDtos.Links::getTiktok))
                .x(pick(menu.isShowX(), fromBranch, own, SocialDtos.Links::getX))
                .build();
    }

    private static String pick(boolean shown, SocialDtos.Links branch, SocialDtos.Links menu,
                               Function<SocialDtos.Links, String> field) {
        if (!shown) {
            return null;
        }
        String value = field.apply(branch);
        return value != null ? value : field.apply(menu);
    }
}
