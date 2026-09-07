package com.ael.algoryqrservice.access;

import com.ael.algoryqrservice.model.TrialLog;
import com.ael.algoryqrservice.model.enums.GrantFulfillmentStatus;
import com.ael.algoryqrservice.model.enums.TrialLogStatus;
import com.ael.algoryqrservice.repository.GrantFulfillmentRepository;
import com.ael.algoryqrservice.repository.TrialLogRepository;
import com.ael.algoryqrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TrialLogCloser {

    private final TrialLogRepository trialLogRepository;
    private final GrantFulfillmentRepository grantFulfillmentRepository;

    @Transactional
    public void endIfActive(Long userId) {
        trialLogRepository.findByUserId(userId)
                .filter(log -> log.getStatus() == TrialLogStatus.ACTIVE)
                .ifPresent(this::end);
    }

    @Transactional
    public void end(TrialLog log) {
        log.setStatus(TrialLogStatus.ENDED);
        log.setEndsAt(AppTime.nowLocal());
        trialLogRepository.save(log);
        grantFulfillmentRepository.findByTrialLogId(log.getId()).ifPresent(fulfillment -> {
            if (fulfillment.getStatus() == GrantFulfillmentStatus.ACTIVE) {
                fulfillment.setStatus(GrantFulfillmentStatus.EXPIRED);
                grantFulfillmentRepository.save(fulfillment);
            }
        });
    }
}
