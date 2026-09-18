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

    private static final Map<StoreOrderStatus, Set<StoreOrderStatus>> ALLOWED = Map.of(
            PENDING, Set.of(CONFIRMED, REJECTED, CANCELLED),
            CONFIRMED, Set.of(PREPARING, CANCELLED),
            PREPARING, Set.of(READY, CANCELLED),
            READY, Set.of(ON_THE_WAY, DELIVERED, CANCELLED),
            ON_THE_WAY, Set.of(DELIVERED),
            DELIVERED, Set.of(),
            REJECTED, Set.of(),
            CANCELLED, Set.of()
    );

    public void requireTransition(StoreOrderStatus from, StoreOrderStatus to) {
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Sipariş " + from + " durumundan " + to + " durumuna geçirilemez"
            );
        }
    }

    public boolean isTerminal(StoreOrderStatus status) {
        return ALLOWED.getOrDefault(status, Set.of()).isEmpty();
    }
}
