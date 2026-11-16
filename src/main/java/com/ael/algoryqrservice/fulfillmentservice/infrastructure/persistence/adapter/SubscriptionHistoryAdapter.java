package com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.adapter;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.SubscriptionEvent;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.SubscriptionHistoryPort;
import com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.entity.SubscriptionHistoryEntity;
import com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.repository.SubscriptionHistoryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SubscriptionHistoryAdapter implements SubscriptionHistoryPort {

    private static final int DETAIL_MAX = 500;

    private final SubscriptionHistoryJpaRepository repository;

    @Override
    public boolean record(SubscriptionEvent event) {
        if (event.dedupeKey() != null && repository.existsByDedupeKey(event.dedupeKey())) {
            return false;
        }
        SubscriptionHistoryEntity entity = new SubscriptionHistoryEntity();
        entity.setMerchantId(event.merchantId());
        entity.setEventType(event.type());
        entity.setPackageCode(event.packageCode());
        entity.setPreviousPackageCode(event.previousPackageCode());
        entity.setStatus(event.status());
        entity.setEndsAt(event.endsAt());
        entity.setDetail(event.detail() == null || event.detail().length() <= DETAIL_MAX
                ? event.detail()
                : event.detail().substring(0, DETAIL_MAX));
        entity.setActor(event.actor() == null ? "system" : event.actor());
        entity.setDedupeKey(event.dedupeKey());
        entity.setCreatedAt(event.occurredAt());
        repository.save(entity);
        return true;
    }

    @Override
    public List<SubscriptionEvent> findByMerchant(Long merchantId, int limit) {
        return repository.findByMerchantIdOrderByCreatedAtDescIdDesc(merchantId, PageRequest.of(0, Math.max(1, limit)))
                .stream()
                .map(entity -> new SubscriptionEvent(
                        entity.getMerchantId(),
                        entity.getEventType(),
                        entity.getPackageCode(),
                        entity.getPreviousPackageCode(),
                        entity.getStatus(),
                        entity.getEndsAt(),
                        entity.getDetail(),
                        entity.getActor(),
                        entity.getDedupeKey(),
                        entity.getCreatedAt()))
                .toList();
    }
}
