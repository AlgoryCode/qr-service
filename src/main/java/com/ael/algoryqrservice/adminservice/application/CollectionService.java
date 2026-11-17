package com.ael.algoryqrservice.adminservice.application;

import com.ael.algoryqrservice.adminservice.dto.AdminBillingDtos.Standing;
import com.ael.algoryqrservice.qrservice.billing.collection.CollectionCase;
import com.ael.algoryqrservice.qrservice.billing.collection.CollectionCaseRepository;
import com.ael.algoryqrservice.qrservice.billing.collection.CollectionEvent;
import com.ael.algoryqrservice.qrservice.billing.collection.CollectionEventRepository;
import com.ael.algoryqrservice.qrservice.billing.collection.CollectionStatus;
import com.ael.algoryqrservice.qrservice.billing.service.RecurringAddonBilling;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.exception.ConflictException;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.model.Purchase;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.qrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Follow-up of unpaid debts. The system opens a case when a merchant falls into arrears, marks it
 * unpaid once the grace time passes and closes it when the money arrives; everything in between
 * (reminders, formal notice, payment plan, legal follow-up, write-off) is an operator decision.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CollectionService {

    /** Days after the due date before an untouched case is marked as not paid. */
    static final int UNPAID_AFTER_DAYS = 15;
    static final int MAX_NOTE_LENGTH = 2000;

    private final CollectionCaseRepository caseRepository;
    private final CollectionEventRepository eventRepository;
    private final PurchaseRepository purchaseRepository;

    /** Opens a case by hand, e.g. for a lapsed merchant who still owes the last period. */
    @Transactional
    public CollectionCase open(Long merchantId, String note, String actor) {
        if (merchantId == null) {
            throw new BadRequestException("merchantId zorunludur");
        }
        caseRepository.findFirstByMerchantIdAndClosedAtIsNull(merchantId).ifPresent(existing -> {
            throw new ConflictException("COLLECTION_CASE_OPEN", "Bu üye için açık bir tahsilat dosyası var");
        });
        Purchase purchase = purchaseRepository.findByMerchantIdOrderByPurchasedAtDesc(merchantId).stream()
                .filter(p -> p.getPurchaseType() == PurchaseType.PAID && p.getPackageId() != null)
                .max(Comparator.comparing(Purchase::getExpiresAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
        return create(merchantId, purchase, clean(note), actor, AppTime.nowLocal());
    }

    /**
     * Moves a case to {@code status} with an optional note, or adds a note alone when status is
     * null or unchanged. Reopening a closed case is allowed while the merchant has no other open one.
     */
    @Transactional
    public CollectionCase record(Long caseId, CollectionStatus status, String note, String actor) {
        CollectionCase collectionCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new NotFoundException("Tahsilat dosyası bulunamadı"));
        String cleanNote = clean(note);
        CollectionStatus target = status == null ? collectionCase.getStatus() : status;
        if (target == collectionCase.getStatus()) {
            if (cleanNote == null) {
                throw new BadRequestException("Durum değişmiyorsa not girilmeli");
            }
            log(collectionCase, collectionCase.getStatus(), target, cleanNote, actor);
            return collectionCase;
        }
        if (!collectionCase.isOpen() && !target.isClosed()) {
            caseRepository.findFirstByMerchantIdAndClosedAtIsNull(collectionCase.getMerchantId())
                    .filter(other -> !other.getId().equals(collectionCase.getId()))
                    .ifPresent(other -> {
                        throw new ConflictException("COLLECTION_CASE_OPEN", "Bu üye için başka bir açık tahsilat dosyası var");
                    });
        }
        transition(collectionCase, target, cleanNote, actor, AppTime.nowLocal());
        return collectionCase;
    }

    @Transactional(readOnly = true)
    public CollectionCase get(Long caseId) {
        return caseRepository.findById(caseId).orElseThrow(() -> new NotFoundException("Tahsilat dosyası bulunamadı"));
    }

    @Transactional(readOnly = true)
    public List<CollectionEvent> history(Long caseId) {
        return eventRepository.findByCaseIdOrderByCreatedAtAscIdAsc(caseId);
    }

    @Transactional(readOnly = true)
    public Map<Long, CollectionCase> openCasesByMerchant() {
        return caseRepository.findByClosedAtIsNull().stream()
                .collect(Collectors.toMap(CollectionCase::getMerchantId, Function.identity(), (a, b) -> a));
    }

    /**
     * Daily pass: opens cases for merchants newly in arrears, closes cases whose debt was paid and
     * marks untouched cases as not paid once {@link #UNPAID_AFTER_DAYS} have passed.
     *
     * @return how many cases were opened or changed
     */
    @Transactional
    public int reconcile() {
        LocalDateTime now = AppTime.nowLocal();
        Map<Long, CollectionCase> open = openCasesByMerchant();
        List<Purchase> current = AdminBillingService.currentPerMerchant(purchaseRepository.findBillingStandingCandidates(
                now.minusDays(AdminBillingService.LAPSED_LOOKBACK_DAYS)));
        int changes = 0;
        for (Purchase purchase : current) {
            CollectionCase existing = open.get(purchase.getMerchantId());
            Standing standing = AdminBillingService.standing(purchase, now, 1);
            if (existing == null) {
                if (standing == Standing.OVERDUE) {
                    open.put(purchase.getMerchantId(),
                            create(purchase.getMerchantId(), purchase, "Yenileme ödemesi alınamadı", CollectionEvent.SYSTEM_ACTOR, now));
                    changes++;
                }
            } else if (paidSince(existing, purchase, standing)) {
                transition(existing, CollectionStatus.PAID, "Ödeme alındı, paket yenilendi", CollectionEvent.SYSTEM_ACTOR, now);
                open.remove(purchase.getMerchantId());
                changes++;
            }
        }
        for (CollectionCase collectionCase : open.values()) {
            if (collectionCase.getStatus().isEarlyStage()
                    && collectionCase.getDueSince() != null
                    && collectionCase.getDueSince().plusDays(UNPAID_AFTER_DAYS).isBefore(now)) {
                transition(collectionCase, CollectionStatus.UNPAID,
                        UNPAID_AFTER_DAYS + " gün içinde ödeme yapılmadı", CollectionEvent.SYSTEM_ACTOR, now);
                changes++;
            }
        }
        return changes;
    }

    /** The merchant's package now runs past the period the case was opened for, and is not in arrears. */
    static boolean paidSince(CollectionCase collectionCase, Purchase purchase, Standing standing) {
        if (standing != Standing.PAID && standing != Standing.DUE_SOON) {
            return false;
        }
        LocalDateTime dueSince = collectionCase.getDueSince();
        return dueSince == null || (purchase.getExpiresAt() != null && purchase.getExpiresAt().isAfter(dueSince));
    }

    private CollectionCase create(Long merchantId, Purchase purchase, String note, String actor, LocalDateTime now) {
        CollectionCase collectionCase = caseRepository.save(CollectionCase.builder()
                .merchantId(merchantId)
                .purchaseId(purchase == null ? null : purchase.getId())
                .status(CollectionStatus.AWAITING_PAYMENT)
                .amount(purchase == null ? null : RecurringAddonBilling.renewalAmount(purchase))
                .currency(purchase == null ? null : Optional.ofNullable(purchase.getCurrency()).orElse("TRY"))
                .dueSince(purchase == null ? now : Optional.ofNullable(purchase.getExpiresAt()).orElse(now))
                .openedAt(now)
                .statusAt(now)
                .build());
        log(collectionCase, null, CollectionStatus.AWAITING_PAYMENT, note, actor);
        log.info("collection_case_opened caseId={} merchantId={} actor={}", collectionCase.getId(), merchantId, actor);
        return collectionCase;
    }

    private void transition(CollectionCase collectionCase, CollectionStatus target, String note, String actor,
                            LocalDateTime now) {
        CollectionStatus from = collectionCase.getStatus();
        collectionCase.setStatus(target);
        collectionCase.setStatusAt(now);
        collectionCase.setClosedAt(target.isClosed() ? now : null);
        caseRepository.save(collectionCase);
        log(collectionCase, from, target, note, actor);
        log.info("collection_case_status caseId={} merchantId={} from={} to={} actor={}",
                collectionCase.getId(), collectionCase.getMerchantId(), from, target, actor);
    }

    private void log(CollectionCase collectionCase, CollectionStatus from, CollectionStatus to, String note, String actor) {
        eventRepository.save(CollectionEvent.builder()
                .caseId(collectionCase.getId())
                .fromStatus(from)
                .toStatus(to)
                .note(note)
                .actor(actor == null || actor.isBlank() ? CollectionEvent.SYSTEM_ACTOR : actor)
                .build());
    }

    private static String clean(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String trimmed = note.trim();
        if (trimmed.length() > MAX_NOTE_LENGTH) {
            throw new BadRequestException("Not en fazla " + MAX_NOTE_LENGTH + " karakter olabilir");
        }
        return trimmed;
    }
}
