package com.ael.algoryqrservice.store.service;

import com.ael.algoryqrservice.store.model.StoreOrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoreOrderStatusMachineTest {

    private final StoreOrderStatusMachine statusMachine = new StoreOrderStatusMachine();

    @Test
    void requireTransition_whenHappyPath_thenAllow() {
        assertThatCode(() -> {
            statusMachine.requireTransition(StoreOrderStatus.PENDING, StoreOrderStatus.CONFIRMED);
            statusMachine.requireTransition(StoreOrderStatus.CONFIRMED, StoreOrderStatus.PREPARING);
            statusMachine.requireTransition(StoreOrderStatus.PREPARING, StoreOrderStatus.READY);
            statusMachine.requireTransition(StoreOrderStatus.READY, StoreOrderStatus.ON_THE_WAY);
            statusMachine.requireTransition(StoreOrderStatus.ON_THE_WAY, StoreOrderStatus.DELIVERED);
        }).doesNotThrowAnyException();
    }

    @Test
    void requireTransition_whenPickupGoesStraightToDelivered_thenAllow() {
        assertThatCode(() -> statusMachine.requireTransition(StoreOrderStatus.READY, StoreOrderStatus.DELIVERED))
                .doesNotThrowAnyException();
    }

    @Test
    void requireTransition_whenSkippingConfirmation_thenThrowConflict() {
        assertThatThrownBy(() -> statusMachine.requireTransition(StoreOrderStatus.PENDING, StoreOrderStatus.READY))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    void requireTransition_whenOrderAlreadyDelivered_thenThrowConflict() {
        assertThatThrownBy(() -> statusMachine.requireTransition(StoreOrderStatus.DELIVERED, StoreOrderStatus.CANCELLED))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void requireTransition_whenDispatchedOrderCancelled_thenThrowConflict() {
        assertThatThrownBy(() -> statusMachine.requireTransition(StoreOrderStatus.ON_THE_WAY, StoreOrderStatus.CANCELLED))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void isTerminal_whenFinalStatus_thenTrue() {
        assertThat(statusMachine.isTerminal(StoreOrderStatus.DELIVERED)).isTrue();
        assertThat(statusMachine.isTerminal(StoreOrderStatus.REJECTED)).isTrue();
        assertThat(statusMachine.isTerminal(StoreOrderStatus.CANCELLED)).isTrue();
        assertThat(statusMachine.isTerminal(StoreOrderStatus.PENDING)).isFalse();
    }
}
