package com.ael.algoryqrservice.qrservice.common.social;

import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.model.Branch;
import com.ael.algoryqrservice.qrservice.model.Menu;
import org.junit.jupiter.api.Test;

import static com.ael.algoryqrservice.qrservice.common.social.SocialLinks.Platform.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SocialLinksTest {

    @Test
    void normalizesHandlesDomainsAndUrls() {
        assertThat(SocialLinks.normalize(INSTAGRAM, "@tavsan.coffee")).isEqualTo("https://www.instagram.com/tavsan.coffee");
        assertThat(SocialLinks.normalize(YOUTUBE, "tavsan")).isEqualTo("https://www.youtube.com/@tavsan");
        assertThat(SocialLinks.normalize(TIKTOK, "@tavsan")).isEqualTo("https://www.tiktok.com/@tavsan");
        assertThat(SocialLinks.normalize(FACEBOOK, "facebook.com/tavsan")).isEqualTo("https://facebook.com/tavsan");
        assertThat(SocialLinks.normalize(X, "https://twitter.com/tavsan")).isEqualTo("https://twitter.com/tavsan");
        assertThat(SocialLinks.normalize(INSTAGRAM, "  ")).isNull();
        assertThat(SocialLinks.normalize(INSTAGRAM, null)).isNull();
    }

    @Test
    void rejectsLinksToAnotherSite() {
        assertThatThrownBy(() -> SocialLinks.normalize(INSTAGRAM, "https://evil.example/instagram.com"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> SocialLinks.normalize(FACEBOOK, "javascript:alert(1)"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void publicLinksPreferBranchAndRespectSwitches() {
        Menu menu = new Menu();
        menu.setShowFacebook(true);
        menu.setShowInstagram(true);
        menu.setShowYoutube(false);
        menu.setShowTiktok(true);
        menu.setShowX(true);
        menu.setInstagramUrl("https://www.instagram.com/menu");
        menu.setTiktokUrl("https://www.tiktok.com/@menu");
        Branch branch = new Branch();
        branch.setInstagramUrl("https://www.instagram.com/branch");
        branch.setYoutubeUrl("https://www.youtube.com/@branch");

        SocialDtos.Links links = SocialLinks.publicLinks(menu, branch);

        assertThat(links.getInstagram()).isEqualTo("https://www.instagram.com/branch");
        assertThat(links.getYoutube()).isNull();
        assertThat(links.getTiktok()).isEqualTo("https://www.tiktok.com/@menu");
        assertThat(links.getFacebook()).isNull();
        assertThat(SocialLinks.publicLinks(menu, null).getInstagram()).isEqualTo("https://www.instagram.com/menu");
    }
}
