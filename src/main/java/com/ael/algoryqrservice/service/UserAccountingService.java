package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuOrder;
import com.ael.algoryqrservice.model.MenuWaiter;
import com.ael.algoryqrservice.model.TableBill;
import com.ael.algoryqrservice.model.UserAccountingEntry;
import com.ael.algoryqrservice.model.dto.UserAccountingDtos;
import com.ael.algoryqrservice.model.enums.AccountingEntryType;
import com.ael.algoryqrservice.model.enums.AccountingSourceType;
import com.ael.algoryqrservice.model.enums.MenuOrderStatus;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.RestaurantTableRepository;
import com.ael.algoryqrservice.repository.UserAccountingEntryRepository;
import com.ael.algoryqrservice.repository.UserAccountingEntrySpecifications;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserAccountingService {

    private final UserAccountingEntryRepository userAccountingEntryRepository;
    private final MenuRepository menuRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final SecurityUtils securityUtils;

    @Transactional
    public UserAccountingDtos.EntryResponse createManual(UserAccountingDtos.CreateRequest request) {
        if (request == null) {
            throw new BadRequestException("İstek gövdesi zorunludur");
        }
        if (request.getEntryType() == null) {
            throw new BadRequestException("Kayıt türü zorunludur");
        }

        Long userId = securityUtils.getCurrentUserId();
        String title = requireText(request.getTitle(), "Başlık zorunludur", 200);
        BigDecimal amount = requirePositiveAmount(request.getAmount());
        LocalDateTime occurredAt = request.getOccurredAt();
        if (occurredAt == null) {
            throw new BadRequestException("İşlem tarihi zorunludur");
        }

        Long menuId = request.getMenuId();
        if (menuId != null) {
            requireOwnedMenu(menuId, userId);
        }

        LocalDateTime now = LocalDateTime.now();
        UserAccountingEntry entry = UserAccountingEntry.builder()
                .userId(userId)
                .entryType(request.getEntryType())
                .title(title)
                .amount(amount)
                .currency("TRY")
                .occurredAt(occurredAt)
                .note(trimToNull(request.getNote()))
                .menuId(menuId)
                .sourceType(AccountingSourceType.MANUAL)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return toResponse(userAccountingEntryRepository.save(entry));
    }

    @Transactional(readOnly = true)
    public UserAccountingDtos.EntryPageResponse listForCurrentUser(
            String type,
            LocalDate from,
            LocalDate to,
            String q,
            int page,
            int size
    ) {
        Long userId = securityUtils.getCurrentUserId();
        AccountingEntryType typeFilter = parseTypeFilter(type);
        LocalDateTime fromDt = from == null ? null : from.atStartOfDay();
        LocalDateTime toDt = to == null ? null : to.plusDays(1).atStartOfDay().minusNanos(1);
        String query = trimToNull(q);
        String pattern = query == null ? null : "%" + query.toLowerCase(Locale.ROOT) + "%";

        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);
        Pageable pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("createdAt"))
        );

        Page<UserAccountingEntry> result = userAccountingEntryRepository.findAll(
                UserAccountingEntrySpecifications.forUser(userId, typeFilter, fromDt, toDt, pattern),
                pageable
        );

        List<UserAccountingEntry> allFiltered = userAccountingEntryRepository.findAll(
                UserAccountingEntrySpecifications.forUser(userId, typeFilter, fromDt, toDt, pattern)
        );

        return new UserAccountingDtos.EntryPageResponse(
                result.getContent().stream().map(this::toResponse).toList(),
                safePage,
                safeSize,
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext(),
                buildSummary(allFiltered)
        );
    }

    @Transactional
    public void deleteManual(Long entryId) {
        Long userId = securityUtils.getCurrentUserId();
        UserAccountingEntry entry = userAccountingEntryRepository.findById(entryId)
                .filter(e -> e.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Kayıt bulunamadı"));
        if (entry.getSourceType() != AccountingSourceType.MANUAL) {
            throw new BadRequestException("Otomatik oluşturulan kayıtlar silinemez");
        }
        userAccountingEntryRepository.delete(entry);
    }

    @Transactional
    public void recordBillCloseIncome(Menu menu, TableBill bill, MenuWaiter waiter, BigDecimal tipAmount) {
        if (menu == null || bill == null) {
            return;
        }

        Long userId = menu.getUserId();
        LocalDateTime occurredAt = bill.getClosedAt() != null ? bill.getClosedAt() : LocalDateTime.now();
        String tableName = restaurantTableRepository.findById(bill.getTableId())
                .map(t -> t.getName())
                .orElse("Masa");

        if (tipAmount != null && tipAmount.compareTo(BigDecimal.ZERO) > 0) {
            createSystemEntry(
                    userId,
                    AccountingEntryType.GELIR,
                    "Bahşiş - " + tableName,
                    tipAmount,
                    bill.getCurrency(),
                    occurredAt,
                    menu.getMenuId(),
                    AccountingSourceType.BILL_TIP,
                    bill.getId(),
                    null,
                    waiter != null ? waiter.getId() : null
            );
        }
    }

    @Transactional
    public void recordConfirmedOrderIncome(MenuOrder order) {
        if (order == null || order.getStatus() != MenuOrderStatus.CONFIRMED) {
            return;
        }
        if (order.getTotalAmount() == null || order.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (userAccountingEntryRepository.existsBySourceTypeAndSourceOrderId(
                AccountingSourceType.ORDER_SALE,
                order.getId()
        )) {
            return;
        }

        Menu menu = menuRepository.findById(order.getMenuId()).orElse(null);
        if (menu == null) {
            return;
        }

        LocalDateTime occurredAt = order.getConfirmedAt() != null
                ? order.getConfirmedAt()
                : order.getSubmittedAt() != null ? order.getSubmittedAt() : order.getCreatedAt();
        if (occurredAt == null) {
            occurredAt = LocalDateTime.now();
        }

        createSystemEntry(
                menu.getUserId(),
                AccountingEntryType.GELIR,
                "Sipariş #" + order.getId(),
                order.getTotalAmount(),
                order.getCurrency(),
                occurredAt,
                menu.getMenuId(),
                AccountingSourceType.ORDER_SALE,
                null,
                order.getId(),
                order.getWaiterId()
        );
    }

    private void createSystemEntry(
            Long userId,
            AccountingEntryType entryType,
            String title,
            BigDecimal amount,
            String currency,
            LocalDateTime occurredAt,
            Long menuId,
            AccountingSourceType sourceType,
            Long sourceBillId,
            Long sourceOrderId,
            Long createdByWaiterId
    ) {
        LocalDateTime now = LocalDateTime.now();
        UserAccountingEntry entry = UserAccountingEntry.builder()
                .userId(userId)
                .entryType(entryType)
                .title(title)
                .amount(amount)
                .currency(currency != null && !currency.isBlank() ? currency : "TRY")
                .occurredAt(occurredAt)
                .menuId(menuId)
                .sourceType(sourceType)
                .sourceBillId(sourceBillId)
                .sourceOrderId(sourceOrderId)
                .createdByWaiterId(createdByWaiterId)
                .createdAt(now)
                .updatedAt(now)
                .build();
        userAccountingEntryRepository.save(entry);
    }

    private UserAccountingDtos.SummaryTotals buildSummary(List<UserAccountingEntry> entries) {
        BigDecimal gelir = BigDecimal.ZERO;
        BigDecimal gider = BigDecimal.ZERO;
        BigDecimal borc = BigDecimal.ZERO;
        for (UserAccountingEntry entry : entries) {
            switch (entry.getEntryType()) {
                case GELIR -> gelir = gelir.add(entry.getAmount());
                case GIDER -> gider = gider.add(entry.getAmount());
                case BORC -> borc = borc.add(entry.getAmount());
            }
        }
        return new UserAccountingDtos.SummaryTotals(gelir, gider, borc, "TRY");
    }

    private UserAccountingDtos.EntryResponse toResponse(UserAccountingEntry entry) {
        String menuName = null;
        if (entry.getMenuId() != null) {
            menuName = menuRepository.findById(entry.getMenuId())
                    .map(Menu::getBusinessName)
                    .orElse(null);
        }
        return new UserAccountingDtos.EntryResponse(
                entry.getId(),
                entry.getEntryType(),
                entry.getTitle(),
                entry.getAmount(),
                entry.getCurrency(),
                entry.getOccurredAt(),
                entry.getNote(),
                entry.getMenuId(),
                menuName,
                entry.getSourceType(),
                entry.getSourceBillId(),
                entry.getSourceOrderId(),
                entry.getCreatedByWaiterId(),
                entry.getCreatedAt()
        );
    }

    private AccountingEntryType parseTypeFilter(String type) {
        if (type == null || type.isBlank() || "all".equalsIgnoreCase(type)) {
            return null;
        }
        try {
            return AccountingEntryType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Geçersiz kayıt türü");
        }
    }

    private void requireOwnedMenu(Long menuId, Long userId) {
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new NotFoundException("Menü bulunamadı"));
        if (!userId.equals(menu.getUserId())) {
            throw new NotFoundException("Menü bulunamadı");
        }
    }

    private BigDecimal requirePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(new BigDecimal("0.01")) < 0) {
            throw new BadRequestException("Tutar 0,01 veya daha büyük olmalıdır");
        }
        return amount;
    }

    private String requireText(String value, String message, int maxLen) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BadRequestException(message);
        }
        if (trimmed.length() > maxLen) {
            throw new BadRequestException(message);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
