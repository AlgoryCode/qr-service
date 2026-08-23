package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.MenuOrder;
import com.ael.algoryqrservice.model.MenuOrderItem;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.MenuWaiter;
import com.ael.algoryqrservice.model.SubCategory;
import com.ael.algoryqrservice.model.TableBill;
import com.ael.algoryqrservice.model.WaiterCommissionRecord;
import com.ael.algoryqrservice.model.dto.TableBillDtos;
import com.ael.algoryqrservice.model.enums.WaiterCommissionRecordType;
import com.ael.algoryqrservice.model.enums.WaiterCommissionType;
import com.ael.algoryqrservice.repository.MenuProductRepository;
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

    @Transactional
    public void recordOrderCommissions(MenuWaiter waiter, MenuOrder order, Long billId) {
        if (waiter == null || order == null || !waiter.isCommissionEnabled()) {
            return;
        }
        if (waiter.getCommissionType() == WaiterCommissionType.PERCENT) {
            recordPercentOrderCommission(waiter, order, billId);
        } else if (waiter.getCommissionType() == WaiterCommissionType.FIXED) {
            BigDecimal amount = recordFixedItemAddCommission(
                    waiter,
                    order.getMenuId(),
                    billId,
                    order.getId(),
                    toCommissionLineItems(order.getItems()),
                    order.getCurrency()
            );
            order.setCommissionAmount(amount);
        }
    }

    @Transactional
    public BigDecimal recordPercentOrderCommission(MenuWaiter waiter, MenuOrder order, Long billId) {
        if (waiter == null || order == null || !waiter.isCommissionEnabled()) {
            return BigDecimal.ZERO;
        }
        if (waiter.getCommissionType() != WaiterCommissionType.PERCENT) {
            return BigDecimal.ZERO;
        }

        BigDecimal value = waiter.getCommissionValue();
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal orderTotal = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal amount = orderTotal
                .multiply(value)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        order.setCommissionAmount(amount);

        WaiterCommissionRecord record = WaiterCommissionRecord.builder()
                .waiterId(waiter.getId())
                .menuId(order.getMenuId())
                .branchId(waiter.getBranchId())
                .billId(billId)
                .orderId(order.getId())
                .recordType(WaiterCommissionRecordType.PERCENT_ORDER)
                .baseAmount(orderTotal)
                .commissionValueSnapshot(value)
                .amount(amount)
                .currency(order.getCurrency() != null ? order.getCurrency() : "TRY")
                .createdAt(LocalDateTime.now())
                .build();
        commissionRecordRepository.save(record);
        return amount;
    }

    @Transactional
    public BigDecimal recordFixedItemAddCommission(
            MenuWaiter waiter,
            Long menuId,
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
                    .menuId(menuId)
                    .branchId(waiter.getBranchId())
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
                .menuId(bill.getMenuId())
                .branchId(waiter.getBranchId())
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
            if (record.getRecordType() == WaiterCommissionRecordType.PERCENT_ORDER) {
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
