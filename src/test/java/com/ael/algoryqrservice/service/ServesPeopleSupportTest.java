package com.ael.algoryqrservice.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServesPeopleSupportTest {

    private final ServesPeopleSupport support = new ServesPeopleSupport();

    @Test
    void normalize_whenBothNull_thenOk() {
        ServesPeopleSupport.Range range = support.normalize(null, null);
        assertThat(range.min()).isNull();
        assertThat(range.max()).isNull();
    }

    @Test
    void normalize_whenOnlyMin_thenThrow() {
        assertThatThrownBy(() -> support.normalize(2, null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void resolveFromSeed_whenNameHasTwoPeople_thenInfer() {
        ServesPeopleSupport.Range range = support.resolveFromSeed(null, null, "Serpme Kahvaltı (2 Kişilik)");
        assertThat(range.min()).isEqualTo(2);
        assertThat(range.max()).isEqualTo(2);
    }

    @Test
    void resolveFromSeed_whenMissing_thenDefaultOne() {
        ServesPeopleSupport.Range range = support.resolveFromSeed(null, null, "Espresso");
        assertThat(range.min()).isEqualTo(1);
        assertThat(range.max()).isEqualTo(1);
    }
}
