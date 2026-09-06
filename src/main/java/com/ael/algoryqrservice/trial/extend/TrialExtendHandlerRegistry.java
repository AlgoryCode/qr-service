package com.ael.algoryqrservice.trial.extend;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.Purchase;
import com.ael.algoryqrservice.trial.domain.TrialLifecycle;
import com.ael.algoryqrservice.trial.domain.TrialSnapshot;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class TrialExtendHandlerRegistry {

    private final Map<TrialLifecycle, TrialExtendHandler> handlers;

    public TrialExtendHandlerRegistry(List<TrialExtendHandler> found) {
        Map<TrialLifecycle, TrialExtendHandler> map = new EnumMap<>(TrialLifecycle.class);
        for (TrialExtendHandler handler : found) {
            TrialExtendHandler previous = map.put(handler.supports(), handler);
            if (previous != null) {
                throw new IllegalStateException("Duplicate trial extend handler for " + handler.supports());
            }
        }
        this.handlers = Map.copyOf(map);
    }

    public Purchase extend(Long userId, TrialSnapshot snapshot, int days) {
        TrialExtendHandler handler = handlers.get(snapshot.lifecycle());
        if (handler == null) {
            throw new BadRequestException("Bu deneme durumunda sure uzatilamaz");
        }
        return handler.extend(userId, snapshot, days);
    }
}
