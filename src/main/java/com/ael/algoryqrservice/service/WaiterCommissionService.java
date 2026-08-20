package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.MenuOrder;
import com.ael.algoryqrservice.model.MenuOrderItem;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.MenuWaiter;
import com.ael.algoryqrservice.model.SubCategory;
import com.ael.algoryqrservice.model.TableBill;
import com.ael.algoryqrservice.model.TableBillItem;
import com.ael.algoryqrservice.model.WaiterCommissionRecord;
import com.ael.algoryqrservice.model.dto.TableBillDtos;
import com.ael.algoryqrservice.model.enums.WaiterCommissionRecordType;
import com.ael.algoryqrservice.model.enums.WaiterCommissionScope;
import com.ael.algoryqrservice.model.enums.WaiterCommissionType;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuWaiterRepository;
import com.ael.algoryqrservice.repository.TableBillRepository;
import com.ael.algoryqrservice.repository.WaiterCommissionRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WaiterCommissionService {

    public static final String COMMISSION_EXEMPT_SUB_CATEGORY_SLUG = "su";

    private static final ZoneId MENU_ZONE = ZoneId.of("Europe/Istanbul");

    private final WaiterCommissionRecordRepository commissionRecordRepository;
    private final MenuTaxonomyService menuTaxonomyService;
    private final MenuProductRepository menuProductRepository;
    private final TableBillRepository tableBillRepository;
    private final MenuWaiterRepository menuWaiterRepository;

    @Transactional
    public void recordOrderCommissions(MenuWaiter waiter, MenuOrder order, Long billId) {
    }

    @Transactional
    public void settleBillCommissions(Long billId) {
        if (billId == null) {
            return;
        }
        TableBill bill = tableBillRepository.findWithItemsById(billId).orElse(null);
        if (bill == null || bill.getStatus() != com.ael.algoryqrservice.model.enums.TableBillStatus.CLOSED) {
            return;
        }
        if (bill.getCommissionSettledAt() != null) {
            return;
        }

        Long closerId = bill.getClosedByWaiterId();
        MenuWaiter closer = closerId != null ? menuWaiterRepository.findById(closerId).orElse(null) : null;
        Map<Long, SubCategory> subMap = menuTaxonomyService.loadSubCategoryMap();
        LocalDateTime now = LocalDateTime.now();
        String currency = bill.getCurrency() != null && !bill.getCurrency().isBlank() ? bill.getCurrency() : "TRY";

        if (bill.getItems() != null) {
            for (TableBillItem item : bill.getItems()) {
                item.setCommissionAmount(BigDecimal.ZERO);
            }
        }

        if (closer != null && closer.isCommissionEnabled()) {
            WaiterCommissionScope scope = closer.getCommissionScope() != null
                    ? closer.getCommissionScope()
                    : WaiterCommissionScope.PER_ITEM;
            if (scope == WaiterCommissionScope.BILL_TOTAL) {
                settleBillTotalCommission(closer, bill, currency, now);
            } else {
                settlePerItemCommissions(closer, bill, subMap, currency, now);
            }
        }

        if (bill.getItems() != null) {
            for (TableBillItem item : bill.getItems()) {
                Long itemWaiterId = item.getAddedByWaiterId() != null ? item.getAddedByWaiterId() : closerId;
                if (itemWaiterId == null || (closer != null && itemWaiterId.equals(closer.getId()))) {
                    continue;
                }
                MenuWaiter itemWaiter = menuWaiterRepository.findById(itemWaiterId).orElse(null);
                if (itemWaiter == null || !itemWaiter.isCommissionEnabled()) {
                    continue;
                }
                WaiterCommissionScope scope = itemWaiter.getCommissionScope() != null
                        ? itemWaiter.getCommissionScope()
                        : WaiterCommissionScope.PER_ITEM;
                if (scope != WaiterCommissionScope.PER_ITEM) {
                    continue;
                }
                applyItemCommission(itemWaiter, item, bill, subMap, currency, now);
            }
        }

        bill.setCommissionSettledAt(now);
        BigDecimal settledTotal = BigDecimal.ZERO;
        if (bill.getItems() != null) {
            for (TableBillItem item : bill.getItems()) {
                if (item.getCommissionAmount() != null) {
                    settledTotal = settledTotal.add(item.getCommissionAmount());
                }
            }
        }
        BigDecimal recordTotal = commissionRecordRepository.sumAmountByBillId(bill.getId());
        if (recordTotal != null && recordTotal.compareTo(settledTotal) > 0) {
            settledTotal = recordTotal;
        }
        bill.setCommissionAmount(settledTotal.setScale(2, RoundingMode.HALF_UP));
        tableBillRepository.save(bill);
    }

    private void settleBillTotalCommission(
            MenuWaiter waiter,
            TableBill bill,
            String currency,
            LocalDateTime now
    ) {
        BigDecimal value = waiter.getCommissionValue();
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal billTotal = bill.getTotalAmount() != null ? bill.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal amount;
        WaiterCommissionRecordType recordType;
        if (waiter.getCommissionType() == WaiterCommissionType.PERCENT) {
            amount = billTotal.multiply(value).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            recordType = WaiterCommissionRecordType.PERCENT_ORDER;
        } else if (waiter.getCommissionType() == WaiterCommissionType.FIXED) {
            amount = value.setScale(2, RoundingMode.HALF_UP);
            recordType = WaiterCommissionRecordType.FIXED_TABLE_CLOSE;
        } else {
            return;
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        commissionRecordRepository.save(WaiterCommissionRecord.builder()
                .waiterId(waiter.getId())
                .menuId(waiter.getMenuId())
                .billId(bill.getId())
                .orderId(null)
                .recordType(recordType)
                .baseAmount(billTotal)
                .commissionValueSnapshot(value)
                .amount(amount)
                .currency(currency)
                .createdAt(now)
                .build());
    }

    private void settlePerItemCommissions(
            MenuWaiter waiter,
            TableBill bill,
            Map<Long, SubCategory> subMap,
            String currency,
            LocalDateTime now
    ) {
        if (bill.getItems() == null) {
            return;
        }
        for (TableBillItem item : bill.getItems()) {
            Long itemWaiterId = item.getAddedByWaiterId() != null ? item.getAddedByWaiterId() : bill.getClosedByWaiterId();
            if (itemWaiterId != null && !itemWaiterId.equals(waiter.getId())) {
                continue;
            }
            applyItemCommission(waiter, item, bill, subMap, currency, now);
        }
    }

    private void applyItemCommission(
            MenuWaiter waiter,
            TableBillItem item,
            TableBill bill,
            Map<Long, SubCategory> subMap,
            String currency,
            LocalDateTime now
    ) {
        BigDecimal value = waiter.getCommissionValue();
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Map<Long, MenuProduct> products = loadProducts(List.of(
                new CommissionLineItem(item.getProductId(), item.getQuantity())
        ));
        MenuProduct product = products.get(item.getProductId());
        if (product == null || !isCommissionEligible(product, subMap)) {
            item.setCommissionAmount(BigDecimal.ZERO);
            return;
        }

        BigDecimal amount;
        WaiterCommissionRecordType recordType;
        BigDecimal baseAmount;
        if (waiter.getCommissionType() == WaiterCommissionType.FIXED) {
            int quantity = Math.max(item.getQuantity(), 0);
            amount = value.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
            baseAmount = BigDecimal.valueOf(quantity);
            recordType = WaiterCommissionRecordType.FIXED_ITEM_ADD;
        } else if (waiter.getCommissionType() == WaiterCommissionType.PERCENT) {
            BigDecimal lineTotal = item.getLineTotal() != null ? item.getLineTotal() : BigDecimal.ZERO;
            amount = lineTotal.multiply(value).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            baseAmount = lineTotal;
            recordType = WaiterCommissionRecordType.PERCENT_ITEM;
        } else {
            return;
        }

        item.setCommissionAmount(amount);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        commissionRecordRepository.save(WaiterCommissionRecord.builder()
                .waiterId(waiter.getId())
                .menuId(waiter.getMenuId())
                .billId(bill.getId())
                .orderId(item.getSourceOrderId())
                .recordType(recordType)
                .baseAmount(baseAmount)
                .commissionValueSnapshot(value)
                .amount(amount)
                .currency(currency)
                .createdAt(now)
                .build());
    }

    @Transactional
    public BigDecimal recordPercentOrderCommission(MenuWaiter waiter, MenuOrder order, Long billId) {
        return BigDecimal.ZERO;
    }

    @Transactional
    public BigDecimal recordFixedItemAddCommission(
            MenuWaiter waiter,
            Long billId,
            Long orderId,
            List<CommissionLineItem> items,
            String currency
    ) {
        if (waiter == null || !waiter.isCommissionEnabled()) {
            return BigDecimal.ZERO;
        }
        if (waiter.getCommissionType() != WaiterCommissionType.FIXED) {
            return BigDecimal.ZERO;
        }
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal value = waiter.getCommissionValue();
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        Map<Long, SubCategory> subMap = menuTaxonomyService.loadSubCategoryMap();
        Map<Long, MenuProduct> productsById = loadProducts(items);
        String resolvedCurrency = currency != null && !currency.isBlank() ? currency : "TRY";
        LocalDateTime now = LocalDateTime.now();
        BigDecimal total = BigDecimal.ZERO;

        for (CommissionLineItem item : items) {
            MenuProduct product = productsById.get(item.productId());
            if (product == null || !isCommissionEligible(product, subMap)) {
                continue;
            }

            int quantity = Math.max(item.quantity(), 0);
            if (quantity == 0) {
                continue;
            }

            BigDecimal amount = value.multiply(BigDecimal.valueOf(quantity));
            WaiterCommissionRecord record = WaiterCommissionRecord.builder()
                    .waiterId(waiter.getId())
                    .menuId(waiter.getMenuId())
                    .billId(billId)
                    .orderId(orderId)
                    .recordType(WaiterCommissionRecordType.FIXED_ITEM_ADD)
                    .baseAmount(BigDecimal.valueOf(quantity))
                    .commissionValueSnapshot(value)
                    .amount(amount)
                    .currency(resolvedCurrency)
                    .createdAt(now)
                    .build();
            commissionRecordRepository.save(record);
            total = total.add(amount);
        }

        return total;
    }

    @Transactional
    public BigDecimal recordFixedTableCloseCommission(MenuWaiter waiter, TableBill bill) {
        if (waiter == null || bill == null || !waiter.isCommissionEnabled()) {
            return BigDecimal.ZERO;
        }
        if (waiter.getCommissionType() != WaiterCommissionType.FIXED) {
            return BigDecimal.ZERO;
        }

        BigDecimal value = waiter.getCommissionValue();
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        WaiterCommissionRecord record = WaiterCommissionRecord.builder()
                .waiterId(waiter.getId())
                .menuId(waiter.getMenuId())
                .billId(bill.getId())
                .orderId(null)
                .recordType(WaiterCommissionRecordType.FIXED_TABLE_CLOSE)
                .baseAmount(bill.getTotalAmount() != null ? bill.getTotalAmount() : BigDecimal.ZERO)
                .commissionValueSnapshot(value)
                .amount(value)
                .currency(bill.getCurrency() != null ? bill.getCurrency() : "TRY")
                .createdAt(LocalDateTime.now())
                .build();
        commissionRecordRepository.save(record);
        return value;
    }

    public boolean isCommissionEligible(MenuProduct product, Map<Long, SubCategory> subMap) {
        if (product == null || product.getSubCategoryId() == null) {
            return true;
        }
        SubCategory sub = subMap.get(product.getSubCategoryId());
        if (sub == null || sub.getSlug() == null) {
            return true;
        }
        return !COMMISSION_EXEMPT_SUB_CATEGORY_SLUG.equalsIgnoreCase(sub.getSlug());
    }

    public boolean isCommissionEligible(Long subCategoryId, Map<Long, SubCategory> subMap) {
        if (subCategoryId == null) {
            return true;
        }
        SubCategory sub = subMap.get(subCategoryId);
        if (sub == null || sub.getSlug() == null) {
            return true;
        }
        return !COMMISSION_EXEMPT_SUB_CATEGORY_SLUG.equalsIgnoreCase(sub.getSlug());
    }

    @Transactional(readOnly = true)
    public TableBillDtos.TodayCommissionSummary getTodaySummary(Long waiterId) {
        LocalDateTime[] range = todayRange();
        List<WaiterCommissionRecord> records = commissionRecordRepository
                .findByWaiterIdAndCreatedAtBetweenOrderByCreatedAtDesc(waiterId, range[0], range[1]);

        BigDecimal percentTotal = BigDecimal.ZERO;
        BigDecimal fixedTableCloseTotal = BigDecimal.ZERO;
        BigDecimal fixedItemAddTotal = BigDecimal.ZERO;
        String currency = "TRY";

        for (WaiterCommissionRecord record : records) {
            if (record.getCurrency() != null && !record.getCurrency().isBlank()) {
                currency = record.getCurrency();
            }
            if (record.getRecordType() == WaiterCommissionRecordType.PERCENT_ORDER
                    || record.getRecordType() == WaiterCommissionRecordType.PERCENT_ITEM) {
                percentTotal = percentTotal.add(record.getAmount());
            } else if (record.getRecordType() == WaiterCommissionRecordType.FIXED_TABLE_CLOSE) {
                fixedTableCloseTotal = fixedTableCloseTotal.add(record.getAmount());
            } else if (record.getRecordType() == WaiterCommissionRecordType.FIXED_ITEM_ADD) {
                fixedItemAddTotal = fixedItemAddTotal.add(record.getAmount());
            }
        }

        BigDecimal total = percentTotal.add(fixedTableCloseTotal).add(fixedItemAddTotal);
        List<TableBillDtos.CommissionRecordResponse> recordResponses = records.stream()
                .map(this::toRecordResponse)
                .toList();

        return TableBillDtos.TodayCommissionSummary.builder()
                .totalAmount(total)
                .currency(currency)
                .percentOrderTotal(percentTotal)
                .fixedTableCloseTotal(fixedTableCloseTotal)
                .fixedItemAddTotal(fixedItemAddTotal)
                .recordCount(records.size())
                .records(recordResponses)
                .build();
    }

    @Transactional(readOnly = true)
    public TableBillDtos.CommissionHistoryResponse getHistory(
            Long waiterId,
            LocalDate from,
            LocalDate to,
            int page,
            int size
    ) {
        LocalDate startDate = from != null ? from : LocalDate.now(MENU_ZONE).minusDays(30);
        LocalDate endDate = to != null ? to : LocalDate.now(MENU_ZONE);
        if (endDate.isBefore(startDate)) {
            throw new BadRequestException("Bitiş tarihi başlangıçtan önce olamaz");
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        Page<WaiterCommissionRecord> result = commissionRecordRepository
                .findByWaiterIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                        waiterId,
                        start,
                        end,
                        PageRequest.of(safePage, safeSize)
                );

        return TableBillDtos.CommissionHistoryResponse.builder()
                .records(result.getContent().stream().map(this::toRecordResponse).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    public List<CommissionLineItem> toCommissionLineItems(List<MenuOrderItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .map(item -> new CommissionLineItem(item.getProductId(), item.getQuantity()))
                .toList();
    }

    private TableBillDtos.CommissionRecordResponse toRecordResponse(WaiterCommissionRecord record) {
        return TableBillDtos.CommissionRecordResponse.builder()
                .id(record.getId())
                .billId(record.getBillId())
                .orderId(record.getOrderId())
                .recordType(record.getRecordType())
                .baseAmount(record.getBaseAmount())
                .commissionValueSnapshot(record.getCommissionValueSnapshot())
                .amount(record.getAmount())
                .currency(record.getCurrency())
                .tableName(null)
                .createdAt(record.getCreatedAt())
                .build();
    }

    private LocalDateTime[] todayRange() {
        LocalDate today = LocalDate.now(MENU_ZONE);
        return new LocalDateTime[]{today.atStartOfDay(), today.atTime(LocalTime.MAX)};
    }

    public TableBillDtos.CommissionRecordResponse toRecordResponse(
            WaiterCommissionRecord record,
            String tableName
    ) {
        return TableBillDtos.CommissionRecordResponse.builder()
                .id(record.getId())
                .billId(record.getBillId())
                .orderId(record.getOrderId())
                .recordType(record.getRecordType())
                .baseAmount(record.getBaseAmount())
                .commissionValueSnapshot(record.getCommissionValueSnapshot())
                .amount(record.getAmount())
                .currency(record.getCurrency())
                .tableName(tableName)
                .createdAt(record.getCreatedAt())
                .build();
    }

    private Map<Long, MenuProduct> loadProducts(List<CommissionLineItem> items) {
        Set<Long> productIds = items.stream()
                .map(CommissionLineItem::productId)
                .collect(Collectors.toSet());

        Map<Long, MenuProduct> productsById = new HashMap<>();
        for (Long productId : productIds) {
            menuProductRepository.findByProductIdAndDeletedFalse(productId)
                    .ifPresent(product -> productsById.put(productId, product));
        }
        return productsById;
    }

    public record CommissionLineItem(Long productId, int quantity) {
    }
}
