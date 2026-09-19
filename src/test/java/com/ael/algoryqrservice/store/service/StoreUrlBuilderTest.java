package com.ael.algoryqrservice.store.service;

import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.store.model.Merchant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class StoreUrlBuilderTest {

    private StoreUrlBuilder storeUrlBuilder;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        appProperties.setUrl("https://algoryqr.com/");
        storeUrlBuilder = new StoreUrlBuilder(appProperties);
    }

    @Test
    void buildUrl_whenMerchantGiven_thenJoinStoreNoAndToken() {
        Merchant merchant = Merchant.builder()
                .storeNo(10427L)
                .slug("kebapci-mehmet")
                .publicToken("Gw2AenXEevm3rFfW")
                .build();

        assertThat(storeUrlBuilder.buildHandle(merchant)).isEqualTo("10427-Gw2AenXEevm3rFfW");
        assertThat(storeUrlBuilder.buildUrl(merchant))
                .isEqualTo("https://algoryqr.com/store/10427-Gw2AenXEevm3rFfW");
    }

    @Test
    void parse_whenHandleIsStoreNoAndToken_thenSplitOnDash() {
        Optional<StoreHandle> parsed = storeUrlBuilder.parse("10427-Gw2AenXEevm3rFfW");

        assertThat(parsed).isPresent();
        assertThat(parsed.get().storeNo()).isEqualTo(10427L);
        assertThat(parsed.get().token()).isEqualTo("Gw2AenXEevm3rFfW");
    }

    @Test
    void parse_whenLegacyHandleContainsSlug_thenKeepStoreNoAndToken() {
        Optional<StoreHandle> parsed = storeUrlBuilder.parse("10427-kebapci-mehmet-Gw2AenXEevm3rFfW");

        assertThat(parsed).isPresent();
        assertThat(parsed.get().storeNo()).isEqualTo(10427L);
        assertThat(parsed.get().token()).isEqualTo("Gw2AenXEevm3rFfW");
    }

    @Test
    void parse_whenStoreNoIsNotNumeric_thenEmpty() {
        assertThat(storeUrlBuilder.parse("abc-Gw2AenXEevm3rFfW")).isEmpty();
    }

    @Test
    void parse_whenHandleIsIncomplete_thenEmpty() {
        assertThat(storeUrlBuilder.parse("10427-")).isEmpty();
        assertThat(storeUrlBuilder.parse("10427")).isEmpty();
        assertThat(storeUrlBuilder.parse("")).isEmpty();
        assertThat(storeUrlBuilder.parse(null)).isEmpty();
    }
}
