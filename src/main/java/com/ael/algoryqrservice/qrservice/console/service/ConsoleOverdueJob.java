package com.ael.algoryqrservice.qrservice.console.service;

import com.ael.algoryqrservice.qrservice.console.domain.ConsoleOverdue;
import com.ael.algoryqrservice.qrservice.console.domain.DeviceRentalPricing;
import com.ael.algoryqrservice.qrservice.model.ConsoleSession;
import com.ael.algoryqrservice.qrservice.model.DeviceSessionSegment;
import com.ael.algoryqrservice.qrservice.repository.ConsoleSessionRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceSessionSegmentRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import com.ael.algoryqrservice.shared.scheduling.SchedulerLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Every two minutes, flags open sessions that ran past their time or were left paused ({@link ConsoleOverdue}),
 * and clears the flag once a session is extended, resumed or no longer late. The owner's panel shows the flagged
 * ones in a warning until they are dealt with.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConsoleOverdueJob {

    private final ConsoleSessionRepository sessionRepository;
    private final DeviceSessionSegmentRepository segmentRepository;
    private final SchedulerLock schedulerLock;

    @Scheduled(fixedDelayString = "${console.overdue.fixed-delay:120000}", initialDelay = 60_000)
    public void run() {
        schedulerLock.runExclusivelyInTransaction("console-overdue", Duration.ofMinutes(2), this::scan);
    }

    /** Returns how many sessions are flagged after the scan. */
    int scan() {
        List<ConsoleSession> open = sessionRepository.findByEndedAtIsNullAndDeletedFalse();
        if (open.isEmpty()) {
            return 0;
        }
        Map<Long, List<DeviceSessionSegment>> segments = segmentRepository
                .findBySessionIdInAndDeletedFalseOrderByStartedAtAscIdAsc(open.stream().map(ConsoleSession::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(DeviceSessionSegment::getSessionId));
        LocalDateTime now = AppTime.nowLocal();
        int flagged = 0;
        for (ConsoleSession session : open) {
            long played = DeviceRentalPricing.quote(
                    ConsoleSessionService.toPricing(segments.getOrDefault(session.getId(), List.of())),
                    DeviceRentalPricing.Rules.NONE, now).playedSeconds();
            ConsoleOverdue.Reason reason = ConsoleOverdue.check(session.getPlannedMinutes(), played, session.getPausedAt(), now);
            String name = reason == null ? null : reason.name();
            if (Objects.equals(name, session.getOverdueReason()) && (reason == null) == (session.getOverdueAt() == null)) {
                if (reason != null) {
                    flagged++;
                }
                continue;
            }
            if (reason == null) {
                session.setOverdueAt(null);
                session.setOverdueReason(null);
            } else {
                if (session.getOverdueAt() == null) {
                    session.setOverdueAt(now);
                    log.info("Console session overdue. sessionId={} merchantId={} reason={}", session.getId(),
                            session.getMerchantId(), reason);
                }
                session.setOverdueReason(name);
                flagged++;
            }
            sessionRepository.save(session);
        }
        return flagged;
    }
}
