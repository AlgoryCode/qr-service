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
    void requireTransition_whenKitchenToCourierFlow_thenAllow() {
        assertThatCode(() -> {
            statusMachine.requireTransition(StoreOrderStatus.CONFIRMED, StoreOrderStatus.KITCHEN_PREPARING);
            statusMachine.requireTransition(StoreOrderStatus.KITCHEN_PREPARING, StoreOrderStatus.KITCHEN_PREPARED);
            statusMachine.requireTransition(StoreOrderStatus.KITCHEN_PREPARED, StoreOrderStatus.KITCHEN_DELIVERED_TO_COURIER);
            statusMachine.requireTransition(StoreOrderStatus.KITCHEN_DELIVERED_TO_COURIER, StoreOrderStatus.COURIER_TAKEN);
            statusMachine.requireTransition(StoreOrderStatus.COURIER_TAKEN, StoreOrderStatus.COURIER_DELIVERED_TO_CUSTOMER);
        }).doesNotThrowAnyException();
    }

    @Test
    void requireTransition_whenKitchenToWaiterFlow_thenAllow() {
        assertThatCode(() -> {
            statusMachine.requireTransition(StoreOrderStatus.KITCHEN_PREPARED, StoreOrderStatus.KITCHEN_DELIVERED_TO_WAITER);
            statusMachine.requireTransition(StoreOrderStatus.KITCHEN_DELIVERED_TO_WAITER, StoreOrderStatus.WAITER_TAKEN);
            statusMachine.requireTransition(StoreOrderStatus.WAITER_TAKEN, StoreOrderStatus.WAITER_DELIVERED_TO_CUSTOMER);
        }).doesNotThrowAnyException();
    }

    @Test
    void requireTransition_whenKitchenPreparingSkipsConfirmation_thenThrowConflict() {
        assertThatThrownBy(() -> statusMachine.requireTransition(StoreOrderStatus.PENDING, StoreOrderStatus.KITCHEN_PREPARING))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void requireKitchenRevert_whenMovingBackOnBoard_thenAllow() {
        assertThatCode(() -> {
            statusMachine.requireKitchenRevert(StoreOrderStatus.KITCHEN_PREPARED, StoreOrderStatus.KITCHEN_PREPARING);
            statusMachine.requireKitchenRevert(StoreOrderStatus.READY, StoreOrderStatus.CONFIRMED);
            statusMachine.requireKitchenRevert(StoreOrderStatus.PREPARING, StoreOrderStatus.CONFIRMED);
        }).doesNotThrowAnyException();
    }

    @Test
    void requireKitchenRevert_whenTargetIsPendingOrForward_thenThrowConflict() {
        assertThatThrownBy(() -> statusMachine.requireKitchenRevert(StoreOrderStatus.CONFIRMED, StoreOrderStatus.PENDING))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> statusMachine.requireKitchenRevert(StoreOrderStatus.PREPARING, StoreOrderStatus.READY))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> statusMachine.requireKitchenRevert(StoreOrderStatus.COURIER_TAKEN, StoreOrderStatus.READY))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void isTerminal_whenFinalStatus_thenTrue() {
        assertThat(statusMachine.isTerminal(StoreOrderStatus.WAITER_DELIVERED_TO_CUSTOMER)).isTrue();
        assertThat(statusMachine.isTerminal(StoreOrderStatus.COURIER_DELIVERED_TO_CUSTOMER)).isTrue();
        assertThat(statusMachine.isTerminal(StoreOrderStatus.KITCHEN_PREPARED)).isFalse();
        assertThat(statusMachine.isTerminal(StoreOrderStatus.DELIVERED)).isTrue();
        assertThat(statusMachine.isTerminal(StoreOrderStatus.REJECTED)).isTrue();
        assertThat(statusMachine.isTerminal(StoreOrderStatus.CANCELLED)).isTrue();
        assertThat(statusMachine.isTerminal(StoreOrderStatus.PENDING)).isFalse();
    }
}
