package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuOrder;
import com.ael.algoryqrservice.model.TableBill;
import com.ael.algoryqrservice.model.UserAccountingEntry;
import com.ael.algoryqrservice.model.dto.UserAccountingDtos;
import com.ael.algoryqrservice.model.enums.AccountingEntryType;
import com.ael.algoryqrservice.model.enums.AccountingLineType;
import com.ael.algoryqrservice.model.enums.AccountingSourceType;
import com.ael.algoryqrservice.model.enums.MenuOrderStatus;
import com.ael.algoryqrservice.model.enums.TableBillStatus;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.RestaurantTableRepository;
import com.ael.algoryqrservice.repository.TableBillRepository;
import com.ael.algoryqrservice.repository.UserAccountingEntryRepository;
import com.ael.algoryqrservice.repository.UserAccountingEntrySpecifications;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserAccountingService {

    private final UserAccountingEntryRepository userAccountingEntryRepository;
    private final TableBillRepository tableBillRepository;
    private final MenuRepository menuRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final SecurityUtils securityUtils;

    @Transactional
    public UserAccountingDtos.LineItemResponse createManual(UserAccountingDtos.CreateRequest request) {
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

        return toLineItem(userAccountingEntryRepository.save(entry), resolveMenuNames());
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
        String pattern = query == null ? null : query.toLowerCase(Locale.ROOT);

        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);

        Map<Long, String> menuNames = resolveMenuNames();
        List<UserAccountingDtos.LineItemResponse> lines = new ArrayList<>();
        lines.addAll(buildBillLines(userId, typeFilter, fromDt, toDt, pattern, menuNames));
        lines.addAll(buildManualLines(userId, typeFilter, fromDt, toDt, pattern, menuNames));

        lines.sort(Comparator
                .comparing(UserAccountingDtos.LineItemResponse::occurredAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(UserAccountingDtos.LineItemResponse::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(UserAccountingDtos.LineItemResponse::id, Comparator.reverseOrder()));

        long totalElements = lines.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);
        int fromIndex = Math.min(safePage * safeSize, lines.size());
        int toIndex = Math.min(fromIndex + safeSize, lines.size());
        List<UserAccountingDtos.LineItemResponse> pageContent = lines.subList(fromIndex, toIndex);

        return new UserAccountingDtos.EntryPageResponse(
                pageContent,
                safePage,
                safeSize,
                totalElements,
                totalPages,
                safePage + 1 < totalPages,
                buildSummary(lines)
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

    private List<UserAccountingDtos.LineItemResponse> buildBillLines(
            Long userId,
            AccountingEntryType typeFilter,
            LocalDateTime fromDt,
            LocalDateTime toDt,
            String pattern,
            Map<Long, String> menuNames
    ) {
        if (typeFilter != null && typeFilter != AccountingEntryType.GELIR) {
            return List.of();
        }

        List<Long> menuIds = menuRepository.findMenuIdsByUserId(userId);
        if (menuIds.isEmpty()) {
            return List.of();
        }

        List<TableBill> closedBills = tableBillRepository.findClosedBillsForMenus(
                menuIds,
                TableBillStatus.CLOSED,
                fromDt,
                toDt
        );

        List<UserAccountingDtos.LineItemResponse> lines = new ArrayList<>();
        for (TableBill bill : closedBills) {
            String tableName = restaurantTableRepository.findById(bill.getTableId())
                    .map(t -> t.getName())
                    .orElse("Masa");
            String saleTitle = "Adisyon - " + tableName;
            BigDecimal orderAmount = bill.getTotalAmount() != null ? bill.getTotalAmount() : BigDecimal.ZERO;
            if (orderAmount.compareTo(BigDecimal.ZERO) > 0 && matchesPattern(pattern, saleTitle, null)) {
                lines.add(new UserAccountingDtos.LineItemResponse(
                        "BILL-" + bill.getId(),
                        AccountingLineType.BILL,
                        AccountingEntryType.GELIR,
                        saleTitle,
                        orderAmount,
                        resolveCurrency(bill.getCurrency()),
                        bill.getClosedAt(),
                        null,
                        bill.getId(),
                        null,
                        menuNames.get(bill.getMenuId()),
                        bill.getUpdatedAt()
                ));
            }

            BigDecimal tipAmount = bill.getTipAmount();
            if (tipAmount != null && tipAmount.compareTo(BigDecimal.ZERO) > 0) {
                String tipTitle = "Bahşiş - " + tableName;
                if (matchesPattern(pattern, tipTitle, null)) {
                    lines.add(new UserAccountingDtos.LineItemResponse(
                            "BILL-TIP-" + bill.getId(),
                            AccountingLineType.BILL,
                            AccountingEntryType.GELIR,
                            tipTitle,
                            tipAmount,
                            resolveCurrency(bill.getCurrency()),
                            bill.getClosedAt(),
                            null,
                            bill.getId(),
                            null,
                            menuNames.get(bill.getMenuId()),
                            bill.getUpdatedAt()
                    ));
                }
            }
        }
        return lines;
    }

    private List<UserAccountingDtos.LineItemResponse> buildManualLines(
            Long userId,
            AccountingEntryType typeFilter,
            LocalDateTime fromDt,
            LocalDateTime toDt,
            String pattern,
            Map<Long, String> menuNames
    ) {
        String searchPattern = pattern == null ? null : "%" + pattern + "%";
        List<UserAccountingEntry> entries = userAccountingEntryRepository.findAll(
                UserAccountingEntrySpecifications.forUser(
                        userId,
                        typeFilter,
                        fromDt,
                        toDt,
                        searchPattern,
                        true
                )
        );

        return entries.stream()
                .map(entry -> toLineItem(entry, menuNames))
                .toList();
    }

    private UserAccountingDtos.LineItemResponse toLineItem(
            UserAccountingEntry entry,
            Map<Long, String> menuNames
    ) {
        return new UserAccountingDtos.LineItemResponse(
                "ENTRY-" + entry.getId(),
                AccountingLineType.MANUAL,
                entry.getEntryType(),
                entry.getTitle(),
                entry.getAmount(),
                entry.getCurrency(),
                entry.getOccurredAt(),
                entry.getNote(),
                entry.getSourceBillId(),
                entry.getId(),
                entry.getMenuId() == null ? null : menuNames.get(entry.getMenuId()),
                entry.getCreatedAt()
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

    private UserAccountingDtos.SummaryTotals buildSummary(List<UserAccountingDtos.LineItemResponse> lines) {
        BigDecimal gelir = BigDecimal.ZERO;
        BigDecimal gider = BigDecimal.ZERO;
        BigDecimal borc = BigDecimal.ZERO;
        for (UserAccountingDtos.LineItemResponse line : lines) {
            switch (line.entryType()) {
                case GELIR -> gelir = gelir.add(line.amount());
                case GIDER -> gider = gider.add(line.amount());
                case BORC -> borc = borc.add(line.amount());
            }
        }
        return new UserAccountingDtos.SummaryTotals(gelir, gider, borc, "TRY");
    }

    private Map<Long, String> resolveMenuNames() {
        Long userId = securityUtils.getCurrentUserId();
        List<Long> menuIds = menuRepository.findMenuIdsByUserId(userId);
        Map<Long, String> menuNames = new HashMap<>();
        for (Long menuId : menuIds) {
            menuRepository.findById(menuId)
                    .map(Menu::getBusinessName)
                    .ifPresent(name -> menuNames.put(menuId, name));
        }
        return menuNames;
    }

    private boolean matchesPattern(String pattern, String title, String note) {
        if (pattern == null || pattern.isBlank()) {
            return true;
        }
        String lowerTitle = title == null ? "" : title.toLowerCase(Locale.ROOT);
        String lowerNote = note == null ? "" : note.toLowerCase(Locale.ROOT);
        return lowerTitle.contains(pattern) || lowerNote.contains(pattern);
    }

    private String resolveCurrency(String currency) {
        return currency != null && !currency.isBlank() ? currency : "TRY";
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
