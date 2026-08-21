package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuOrder;
import com.ael.algoryqrservice.model.MenuOrderItem;
import com.ael.algoryqrservice.model.TableBill;
import com.ael.algoryqrservice.model.TableBillItem;
import com.ael.algoryqrservice.model.UserAccountingEntry;
import com.ael.algoryqrservice.model.dto.UserAccountingDtos;
import com.ael.algoryqrservice.model.enums.AccountingEntryType;
import com.ael.algoryqrservice.model.enums.AccountingSourceType;
import com.ael.algoryqrservice.model.enums.TableBillStatus;
import com.ael.algoryqrservice.repository.MenuOrderItemRepository;
import com.ael.algoryqrservice.repository.MenuOrderRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.RestaurantTableRepository;
import com.ael.algoryqrservice.repository.TableBillItemRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserAccountingService {

    private final UserAccountingEntryRepository userAccountingEntryRepository;
    private final TableBillRepository tableBillRepository;
    private final TableBillItemRepository tableBillItemRepository;
    private final MenuRepository menuRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final MenuOrderRepository menuOrderRepository;
    private final MenuOrderItemRepository menuOrderItemRepository;
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

        return toEntryResponse(userAccountingEntryRepository.save(entry), resolveMenuNames(userId));
    }

    @Transactional
    public void recordBillSale(Long billId) {
        if (billId == null) {
            return;
        }
        if (userAccountingEntryRepository.existsBySourceTypeAndSourceBillId(AccountingSourceType.BILL_SALE, billId)) {
            return;
        }

        TableBill bill = tableBillRepository.findWithItemsById(billId)
                .orElse(null);
        if (bill == null || bill.getStatus() != TableBillStatus.CLOSED) {
            return;
        }

        BigDecimal amount = bill.getTotalAmount() != null ? bill.getTotalAmount() : BigDecimal.ZERO;
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        Menu menu = menuRepository.findById(bill.getMenuId()).orElse(null);
        if (menu == null || menu.getUserId() == null) {
            return;
        }

        String tableName = restaurantTableRepository.findById(bill.getTableId())
                .map(t -> t.getName())
                .orElse("Masa");

        List<TableBillItem> items = bill.getItems();
        if (items == null || items.isEmpty()) {
            items = tableBillItemRepository.findByBillId(billId);
        }
        Long sourceOrderId = resolveSingleSourceOrderId(items);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime occurredAt = bill.getClosedAt() != null ? bill.getClosedAt() : now;

        UserAccountingEntry entry = UserAccountingEntry.builder()
                .userId(menu.getUserId())
                .entryType(AccountingEntryType.GELIR)
                .title("Adisyon - " + tableName)
                .amount(amount)
                .currency(resolveCurrency(bill.getCurrency()))
                .occurredAt(occurredAt)
                .menuId(bill.getMenuId())
                .sourceType(AccountingSourceType.BILL_SALE)
                .sourceBillId(bill.getId())
                .sourceOrderId(sourceOrderId)
                .createdByWaiterId(bill.getClosedByWaiterId())
                .orderAmount(amount)
                .createdAt(now)
                .updatedAt(now)
                .build();

        try {
            userAccountingEntryRepository.save(entry);
        } catch (Exception ex) {
            if (userAccountingEntryRepository.existsBySourceTypeAndSourceBillId(AccountingSourceType.BILL_SALE, billId)) {
                return;
            }
            throw ex;
        }
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

        Map<Long, String> menuNames = resolveMenuNames(userId);
        List<UserAccountingEntry> entries = userAccountingEntryRepository.findAll(
                UserAccountingEntrySpecifications.forUserListedSources(
                        userId,
                        typeFilter,
                        fromDt,
                        toDt,
                        pattern
                )
        );

        List<UserAccountingDtos.EntryResponse> lines = entries.stream()
                .map(entry -> toEntryResponse(entry, menuNames))
                .sorted(Comparator
                        .comparing(UserAccountingDtos.EntryResponse::occurredAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(UserAccountingDtos.EntryResponse::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(UserAccountingDtos.EntryResponse::id, Comparator.reverseOrder()))
                .toList();

        long totalElements = lines.size();
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);
        int fromIndex = Math.min(safePage * safeSize, lines.size());
        int toIndex = Math.min(fromIndex + safeSize, lines.size());
        List<UserAccountingDtos.EntryResponse> pageContent = lines.subList(fromIndex, toIndex);

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

    @Transactional(readOnly = true)
    public UserAccountingDtos.EntryDetailResponse getDetailForCurrentUser(Long entryId) {
        Long userId = securityUtils.getCurrentUserId();
        UserAccountingEntry entry = userAccountingEntryRepository.findById(entryId)
                .filter(e -> e.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Kayıt bulunamadı"));

        if (entry.getSourceType() == AccountingSourceType.BILL_TIP) {
            throw new NotFoundException("Kayıt bulunamadı");
        }

        List<UserAccountingDtos.DetailLineItem> items = new ArrayList<>();

        if (entry.getSourceOrderId() != null) {
            MenuOrder order = menuOrderRepository.findById(entry.getSourceOrderId()).orElse(null);
            if (order != null) {
                requireOwnedMenu(order.getMenuId(), userId);
                List<MenuOrderItem> orderItems = menuOrderItemRepository.findByOrderId(order.getId());
                for (MenuOrderItem item : orderItems) {
                    items.add(new UserAccountingDtos.DetailLineItem(
                            item.getId(),
                            item.getProductName(),
                            item.getUnitPrice(),
                            item.getQuantity(),
                            item.getLineTotal(),
                            item.getNote(),
                            order.getId()
                    ));
                }
            }
        } else if (entry.getSourceBillId() != null) {
            TableBill bill = tableBillRepository.findWithItemsById(entry.getSourceBillId()).orElse(null);
            if (bill != null) {
                requireOwnedMenu(bill.getMenuId(), userId);
                List<TableBillItem> billItems = bill.getItems();
                if (billItems == null || billItems.isEmpty()) {
                    billItems = tableBillItemRepository.findByBillId(bill.getId());
                }
                for (TableBillItem item : billItems) {
                    items.add(new UserAccountingDtos.DetailLineItem(
                            item.getId(),
                            item.getProductName(),
                            item.getUnitPrice(),
                            item.getQuantity(),
                            item.getLineTotal(),
                            item.getNote(),
                            item.getSourceOrderId()
                    ));
                }
            }
        }

        return new UserAccountingDtos.EntryDetailResponse(
                entry.getId(),
                entry.getSourceType(),
                entry.getSourceBillId(),
                entry.getSourceOrderId(),
                entry.getTitle(),
                entry.getAmount(),
                entry.getCurrency(),
                items
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

    private Long resolveSingleSourceOrderId(List<TableBillItem> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        Set<Long> orderIds = new HashSet<>();
        for (TableBillItem item : items) {
            if (item.getSourceOrderId() != null) {
                orderIds.add(item.getSourceOrderId());
            }
        }
        if (orderIds.size() == 1) {
            return orderIds.iterator().next();
        }
        return null;
    }

    private UserAccountingDtos.EntryResponse toEntryResponse(
            UserAccountingEntry entry,
            Map<Long, String> menuNames
    ) {
        return new UserAccountingDtos.EntryResponse(
                entry.getId(),
                entry.getEntryType(),
                entry.getTitle(),
                entry.getAmount(),
                entry.getCurrency(),
                entry.getOccurredAt(),
                entry.getNote(),
                entry.getMenuId(),
                entry.getMenuId() == null ? null : menuNames.get(entry.getMenuId()),
                entry.getSourceType(),
                entry.getSourceBillId(),
                entry.getSourceOrderId(),
                entry.getCreatedByWaiterId(),
                entry.getCreatedAt()
        );
    }

    private UserAccountingDtos.SummaryTotals buildSummary(List<UserAccountingDtos.EntryResponse> lines) {
        BigDecimal gelir = BigDecimal.ZERO;
        BigDecimal gider = BigDecimal.ZERO;
        BigDecimal borc = BigDecimal.ZERO;
        for (UserAccountingDtos.EntryResponse line : lines) {
            switch (line.entryType()) {
                case GELIR -> gelir = gelir.add(line.amount());
                case GIDER -> gider = gider.add(line.amount());
                case BORC -> borc = borc.add(line.amount());
            }
        }
        return new UserAccountingDtos.SummaryTotals(gelir, gider, borc, "TRY");
    }

    private Map<Long, String> resolveMenuNames(Long userId) {
        List<Long> menuIds = menuRepository.findMenuIdsByUserId(userId);
        Map<Long, String> menuNames = new HashMap<>();
        for (Long menuId : menuIds) {
            menuRepository.findById(menuId)
                    .map(Menu::getBusinessName)
                    .ifPresent(name -> menuNames.put(menuId, name));
        }
        return menuNames;
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
