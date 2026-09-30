package com.ael.algoryqrservice.store.service;

import com.ael.algoryqrservice.store.model.StoreOrderStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;

import static com.ael.algoryqrservice.store.model.StoreOrderStatus.*;

@Component
public class StoreOrderStatusMachine {

    private static final Set<StoreOrderStatus> AFTER_KITCHEN_READY = Set.of(
            ON_THE_WAY, DELIVERED, KITCHEN_DELIVERED_TO_WAITER, KITCHEN_DELIVERED_TO_COURIER,
            WAITER_TAKEN, COURIER_TAKEN, CANCELLED
    );

    private static final Map<StoreOrderStatus, Set<StoreOrderStatus>> ALLOWED = Map.ofEntries(
            Map.entry(PENDING, Set.of(CONFIRMED, REJECTED, CANCELLED)),
            Map.entry(CONFIRMED, Set.of(PREPARING, KITCHEN_PREPARING, WAITER_TAKEN, CANCELLED)),
            Map.entry(PREPARING, Set.of(READY, KITCHEN_PREPARED, WAITER_TAKEN, CANCELLED)),
            Map.entry(KITCHEN_PREPARING, Set.of(KITCHEN_PREPARED, READY, WAITER_TAKEN, CANCELLED)),
            Map.entry(READY, AFTER_KITCHEN_READY),
            Map.entry(KITCHEN_PREPARED, AFTER_KITCHEN_READY),
            Map.entry(KITCHEN_DELIVERED_TO_WAITER, Set.of(WAITER_TAKEN, CANCELLED)),
            Map.entry(KITCHEN_DELIVERED_TO_COURIER, Set.of(COURIER_TAKEN, WAITER_TAKEN, ON_THE_WAY)),
            Map.entry(WAITER_TAKEN, Set.of(WAITER_DELIVERED_TO_CUSTOMER, WAITER_DELIVERED_TO_COURIER, DELIVERED)),
            Map.entry(WAITER_DELIVERED_TO_COURIER, Set.of(COURIER_TAKEN, WAITER_DELIVERED_TO_CUSTOMER, ON_THE_WAY)),
            Map.entry(COURIER_TAKEN, Set.of(COURIER_DELIVERED_TO_CUSTOMER, WAITER_DELIVERED_TO_CUSTOMER, DELIVERED)),
            Map.entry(ON_THE_WAY, Set.of(DELIVERED, COURIER_DELIVERED_TO_CUSTOMER)),
            Map.entry(DELIVERED, Set.of()),
            Map.entry(WAITER_DELIVERED_TO_CUSTOMER, Set.of()),
            Map.entry(COURIER_DELIVERED_TO_CUSTOMER, Set.of()),
            Map.entry(REJECTED, Set.of()),
            Map.entry(CANCELLED, Set.of())
    );

    public void requireTransition(StoreOrderStatus from, StoreOrderStatus to) {
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Sipariş " + from + " durumundan " + to + " durumuna geçirilemez"
            );
        }
    }

    /** Kitchen board backward moves; stock stays consumed, so the order never returns to PENDING. */
    private static final Map<StoreOrderStatus, Set<StoreOrderStatus>> KITCHEN_REVERT = Map.of(
            PREPARING, Set.of(CONFIRMED),
            KITCHEN_PREPARING, Set.of(CONFIRMED),
            READY, Set.of(PREPARING, KITCHEN_PREPARING, CONFIRMED),
            KITCHEN_PREPARED, Set.of(PREPARING, KITCHEN_PREPARING, CONFIRMED)
    );

    public void requireKitchenRevert(StoreOrderStatus from, StoreOrderStatus to) {
        if (!KITCHEN_REVERT.getOrDefault(from, Set.of()).contains(to)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Sipariş " + from + " durumundan " + to + " durumuna geri alınamaz"
            );
        }
    }

    public boolean isTerminal(StoreOrderStatus status) {
        return ALLOWED.getOrDefault(status, Set.of()).isEmpty();
    }
}
