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
    void buildUrl_whenMerchantGiven_thenJoinStoreNoSlugAndToken() {
        Merchant merchant = Merchant.builder()
                .storeNo(10427L)
                .slug("kebapci-mehmet")
                .publicToken("9fK2xQ7mZa")
                .build();

        assertThat(storeUrlBuilder.buildHandle(merchant)).isEqualTo("10427-kebapci-mehmet-9fK2xQ7mZa");
        assertThat(storeUrlBuilder.buildUrl(merchant))
                .isEqualTo("https://algoryqr.com/store/10427-kebapci-mehmet-9fK2xQ7mZa");
    }

    @Test
    void parse_whenSlugContainsDashes_thenSplitOnFirstAndLastDash() {
        Optional<StoreHandle> parsed = storeUrlBuilder.parse("10427-kebapci-mehmet-9fK2xQ7mZa");

        assertThat(parsed).isPresent();
        assertThat(parsed.get().storeNo()).isEqualTo(10427L);
        assertThat(parsed.get().slug()).isEqualTo("kebapci-mehmet");
        assertThat(parsed.get().token()).isEqualTo("9fK2xQ7mZa");
    }

    @Test
    void parse_whenStoreNoIsNotNumeric_thenEmpty() {
        assertThat(storeUrlBuilder.parse("abc-kebapci-token")).isEmpty();
    }

    @Test
    void parse_whenHandleIsIncomplete_thenEmpty() {
        assertThat(storeUrlBuilder.parse("10427-kebapci")).isEmpty();
        assertThat(storeUrlBuilder.parse("10427-kebapci-")).isEmpty();
        assertThat(storeUrlBuilder.parse("10427")).isEmpty();
        assertThat(storeUrlBuilder.parse("")).isEmpty();
        assertThat(storeUrlBuilder.parse(null)).isEmpty();
    }
}
