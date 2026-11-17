package com.ael.algoryqrservice.qrservice.order.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MenuOrderAssistantQuantityTest {

    @Test
    void missingOrNegativeMeansNoAssistantUnits() {
        assertThat(MenuOrderService.assistantQuantity(null, 3)).isZero();
        assertThat(MenuOrderService.assistantQuantity(0, 3)).isZero();
        assertThat(MenuOrderService.assistantQuantity(-2, 3)).isZero();
    }

    @Test
    void assistantUnitsNeverExceedTheLineQuantity() {
        assertThat(MenuOrderService.assistantQuantity(2, 3)).isEqualTo(2);
        assertThat(MenuOrderService.assistantQuantity(5, 3)).isEqualTo(3);
    }
}
