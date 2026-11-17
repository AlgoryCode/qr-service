package com.ael.algoryqrservice.adminservice.application;

import com.ael.algoryqrservice.adminservice.dto.AdminBillingDtos.Standing;
import com.ael.algoryqrservice.adminservice.dto.AdminMemberDtos.Activity;
import com.ael.algoryqrservice.adminservice.dto.AdminMemberDtos.NoteResponse;
import com.ael.algoryqrservice.adminservice.dto.AdminMemberDtos.Overview;
import com.ael.algoryqrservice.adminservice.dto.AdminMemberDtos.TagCount;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.AdminAuditLog;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.AdminGrant;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.MemberNote;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.MemberTag;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.repository.AdminAuditLogRepository;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.repository.AdminGrantRepository;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.repository.MemberNoteRepository;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.repository.MemberTagRepository;
import com.ael.algoryqrservice.qrservice.billing.collection.CollectionCase;
import com.ael.algoryqrservice.qrservice.billing.collection.CollectionCaseRepository;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.model.Purchase;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.qrservice.model.enums.RefundStatus;
import com.ael.algoryqrservice.qrservice.repository.PurchaseRepository;
import com.ael.algoryqrservice.qrservice.repository.UserRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Notes, tags and the 360 overview of one member. */
@Service
@RequiredArgsConstructor
public class AdminMemberService {

    static final int DUE_WINDOW_DAYS = 7;
    static final int ACTIVITY_LIMIT = 40;
    private static final Pattern TAG = Pattern.compile("^[\\p{L}\\p{N}][\\p{L}\\p{N} ._-]{0,39}$");

    private final UserRepository userRepository;
    private final PurchaseRepository purchaseRepository;
    private final CollectionCaseRepository collectionCaseRepository;
    private final MemberNoteRepository noteRepository;
    private final MemberTagRepository tagRepository;
    private final AdminGrantRepository grantRepository;
    private final AdminAuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public Overview overview(Long merchantId) {
        requireMember(merchantId);
        LocalDateTime now = AppTime.nowLocal();
        List<Purchase> purchases = purchaseRepository.findByMerchantIdOrderByPurchasedAtDesc(merchantId);

        List<Purchase> packages = purchases.stream()
                .filter(p -> p.getPurchaseType() == PurchaseType.PAID && p.getPackageId() != null)
                .filter(p -> p.getStatus() == PurchaseStatus.ACTIVE || p.getStatus() == PurchaseStatus.EXPIRED)
                .toList();
        Purchase current = AdminBillingService.currentPerMerchant(packages).stream().findFirst().orElse(null);
        Standing standing = current == null ? null : AdminBillingService.standing(current, now, DUE_WINDOW_DAYS);

        List<Purchase> paid = purchases.stream().filter(AdminMemberService::wasPaid).toList();
        BigDecimal totalPaid = paid.stream().map(Purchase::getPrice).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refunded = purchases.stream()
                .filter(p -> p.getRefundStatus() == RefundStatus.COMPLETED || p.getRefundedAt() != null)
                .map(Purchase::getPrice).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDateTime firstPaid = paid.stream().map(AdminMemberService::paidAt).filter(Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(null);
        LocalDateTime lastPaid = paid.stream().map(AdminMemberService::paidAt).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);

        CollectionCase openCase = collectionCaseRepository.findFirstByMerchantIdAndClosedAtIsNull(merchantId).orElse(null);
        List<MemberNote> notes = noteRepository.findByMerchantIdOrderByPinnedDescCreatedAtDesc(merchantId);
        List<AdminGrant> grants = grantRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId);

        return new Overview(
                merchantId,
                standing,
                current == null ? null : current.getPackageName(),
                current == null ? null : current.getExpiresAt(),
                current == null || current.getExpiresAt() == null ? null : AdminBillingService.daysBetween(now, current.getExpiresAt()),
                totalPaid,
                refunded,
                paid.size(),
                paid.stream().map(Purchase::getCurrency).filter(Objects::nonNull).findFirst().orElse("TRY"),
                firstPaid,
                lastPaid,
                openCase == null ? null : openCase.getId(),
                openCase == null ? null : openCase.getStatus(),
                tags(merchantId),
                notes.size(),
                (int) grants.stream().filter(g -> g.isActive(now)).count(),
                activity(merchantId, purchases, grants, notes)
        );
    }

    private List<Activity> activity(Long merchantId, List<Purchase> purchases, List<AdminGrant> grants, List<MemberNote> notes) {
        List<Activity> items = new ArrayList<>();
        Set<Long> grantPurchases = new java.util.HashSet<>(grants.stream().map(AdminGrant::getPurchaseId).toList());
        for (Purchase p : purchases) {
            if (grantPurchases.contains(p.getId())) {
                continue;
            }
            if (p.getPurchasedAt() == null || p.getStatus() == PurchaseStatus.PENDING || p.getStatus() == PurchaseStatus.FAILED) {
                continue;
            }
            String kind = p.getPurchaseType() == PurchaseType.ADD_ON ? "ADDON" : "PURCHASE";
            String detail = p.getPrice() == null ? null : p.getPrice().toPlainString() + " " + Objects.requireNonNullElse(p.getCurrency(), "TRY");
            items.add(new Activity(paidAt(p) == null ? p.getPurchasedAt() : paidAt(p), kind, p.getPackageName(), detail, null));
            if (p.getRefundedAt() != null) {
                items.add(new Activity(p.getRefundedAt(), "REFUND", p.getPackageName(), detail, null));
            }
        }
        for (AdminGrant g : grants) {
            items.add(new Activity(g.getCreatedAt(), "GRANT", g.getProductName() + " × " + g.getQuantity(), g.getReason(), g.getGrantedBy()));
            if (g.getRevokedAt() != null) {
                items.add(new Activity(g.getRevokedAt(), "GRANT_REVOKED", g.getProductName(), null, g.getRevokedBy()));
            }
        }
        for (MemberNote n : notes) {
            items.add(new Activity(n.getCreatedAt(), "NOTE", "Not eklendi", abbreviate(n.getBody(), 140), n.getAuthorName()));
        }
        String id = String.valueOf(merchantId);
        Specification<AdminAuditLog> spec = (root, q, cb) -> cb.and(
                root.get("targetType").in("users", "members"),
                cb.equal(root.get("targetId"), id),
                cb.equal(root.get("outcome"), "SUCCESS"));
        auditLogRepository.findAll(spec, PageRequest.of(0, ACTIVITY_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream()
                // Grants and notes already have their own, richer lines.
                .filter(a -> a.getAction() == null || !a.getAction().matches(".*/(grants|notes|tags)(/.*)?$"))
                .forEach(a -> items.add(new Activity(a.getCreatedAt(), "ADMIN", a.getAction(), null, a.getActorName())));
        return items.stream()
                .filter(a -> a.at() != null)
                .sorted(Comparator.comparing(Activity::at).reversed())
                .limit(ACTIVITY_LIMIT)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NoteResponse> notes(Long merchantId) {
        return noteRepository.findByMerchantIdOrderByPinnedDescCreatedAtDesc(merchantId).stream()
                .map(AdminMemberService::toResponse).toList();
    }

    @Transactional
    public NoteResponse addNote(Long merchantId, String body, boolean pinned, Long authorId, String authorName) {
        requireMember(merchantId);
        LocalDateTime now = AppTime.nowLocal();
        return toResponse(noteRepository.save(MemberNote.builder()
                .merchantId(merchantId).body(body.trim()).pinned(pinned)
                .authorId(authorId).authorName(authorName)
                .createdAt(now).updatedAt(now).build()));
    }

    @Transactional
    public NoteResponse updateNote(Long merchantId, Long noteId, String body, Boolean pinned) {
        MemberNote note = noteRepository.findById(noteId)
                .filter(n -> n.getMerchantId().equals(merchantId))
                .orElseThrow(() -> new NotFoundException("Not bulunamadı"));
        if (body != null && !body.isBlank()) {
            note.setBody(body.trim());
        }
        if (pinned != null) {
            note.setPinned(pinned);
        }
        note.setUpdatedAt(AppTime.nowLocal());
        return toResponse(noteRepository.save(note));
    }

    @Transactional
    public void deleteNote(Long merchantId, Long noteId) {
        noteRepository.findById(noteId)
                .filter(n -> n.getMerchantId().equals(merchantId))
                .ifPresent(noteRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<String> tags(Long merchantId) {
        return tagRepository.findByMerchantIdOrderByTagAsc(merchantId).stream().map(MemberTag::getTag).toList();
    }

    /** Replaces the member's tags; tags are lower-cased so "VIP" and "vip" are one. */
    @Transactional
    public List<String> setTags(Long merchantId, List<String> raw, String actor) {
        requireMember(merchantId);
        Set<String> wanted = new LinkedHashSet<>();
        for (String tag : raw) {
            String normalized = normalizeTag(tag);
            if (!TAG.matcher(normalized).matches()) {
                throw new BadRequestException("Geçersiz etiket: " + tag);
            }
            wanted.add(normalized);
        }
        List<MemberTag> existing = tagRepository.findByMerchantIdOrderByTagAsc(merchantId);
        existing.stream().filter(t -> !wanted.contains(t.getTag())).forEach(tagRepository::delete);
        Set<String> have = new LinkedHashSet<>(existing.stream().map(MemberTag::getTag).toList());
        LocalDateTime now = AppTime.nowLocal();
        wanted.stream().filter(t -> !have.contains(t)).forEach(t -> tagRepository.save(
                MemberTag.builder().merchantId(merchantId).tag(t).createdBy(actor).createdAt(now).build()));
        return wanted.stream().sorted().toList();
    }

    @Transactional(readOnly = true)
    public List<TagCount> allTags() {
        return tagRepository.countByTag().stream().map(t -> new TagCount(t.getTag(), t.getCount())).toList();
    }

    static String normalizeTag(String tag) {
        // Root locale keeps "VIP" as "vip"; the dotted capital İ is folded to i by hand.
        return tag == null ? "" : tag.trim().replaceAll("\\s+", " ").replace('İ', 'i').toLowerCase(Locale.ROOT);
    }

    private void requireMember(Long merchantId) {
        if (!userRepository.existsById(merchantId)) {
            throw new NotFoundException("Üye bulunamadı");
        }
    }

    static boolean wasPaid(Purchase p) {
        return p.getPrice() != null && p.getPrice().signum() > 0
                && (p.getStatus() == PurchaseStatus.ACTIVE || p.getStatus() == PurchaseStatus.EXPIRED
                || p.getStatus() == PurchaseStatus.SUPERSEDED || p.getRefundedAt() != null);
    }

    private static LocalDateTime paidAt(Purchase p) {
        return p.getCurrentPeriodPaidAt() != null ? p.getCurrentPeriodPaidAt()
                : p.getStartsAt() != null ? p.getStartsAt() : p.getPurchasedAt();
    }

    private static String abbreviate(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    private static NoteResponse toResponse(MemberNote n) {
        return new NoteResponse(n.getId(), n.getBody(), n.isPinned(), n.getAuthorName(), n.getCreatedAt(), n.getUpdatedAt());
    }
}
