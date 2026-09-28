package com.ael.algoryqrservice.demoonboarding;

import com.ael.algoryqrservice.model.BillPayment;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.TableBill;
import com.ael.algoryqrservice.model.TableBillItem;
import com.ael.algoryqrservice.model.enums.TableBillPaymentMethod;
import com.ael.algoryqrservice.model.enums.TableBillStatus;
import com.ael.algoryqrservice.repository.BillPaymentRepository;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.TableBillRepository;
import com.ael.algoryqrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class DemoOnboardingSalesSeedService {

    private final TableBillRepository tableBillRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final MenuProductRepository menuProductRepository;

    @Transactional
    public int seedFromTemplateOrSynthetic(
            Long sourceMenuId,
            Long targetMenuId,
            Long targetTableId,
            Map<Long, Long> productIdsBySourceId,
            Long userId,
            int backfillDays,
            int maxBills
    ) {
        if (targetMenuId == null || targetTableId == null) {
            return 0;
        }
        LocalDateTime now = AppTime.nowLocal();
        LocalDateTime from = now.minusDays(Math.max(backfillDays, 1));

        List<TableBill> templateBills = tableBillRepository
                .findByMenuIdInAndStatusAndClosedAtBetweenOrderByClosedAtAsc(
                        List.of(sourceMenuId),
                        TableBillStatus.CLOSED,
                        from,
                        now
                );
        if (templateBills.isEmpty()) {
            templateBills = tableBillRepository.findByMenuIdAndStatus(sourceMenuId, TableBillStatus.CLOSED).stream()
                    .filter(bill -> bill.getClosedAt() != null)
                    .sorted(Comparator.comparing(TableBill::getClosedAt).reversed())
                    .limit(maxBills)
                    .sorted(Comparator.comparing(TableBill::getClosedAt))
                    .map(bill -> tableBillRepository.findWithItemsById(bill.getId()).orElse(bill))
                    .toList();
        }
        if (!templateBills.isEmpty()) {
            return cloneShiftedBills(templateBills, targetMenuId, targetTableId, productIdsBySourceId, now, maxBills);
        }
        return synthesizeBills(targetMenuId, targetTableId, userId, from, now, maxBills);
    }

    private int cloneShiftedBills(
            List<TableBill> templateBills,
            Long targetMenuId,
            Long targetTableId,
            Map<Long, Long> productIdsBySourceId,
            LocalDateTime now,
            int maxBills
    ) {
        LocalDateTime latestClosed = templateBills.stream()
                .map(TableBill::getClosedAt)
                .max(LocalDateTime::compareTo)
                .orElse(now);
        Duration shift = Duration.between(latestClosed, now.minusHours(3));
        int created = 0;
        for (TableBill source : templateBills) {
            if (created >= maxBills) {
                break;
            }
            if (source.getClosedAt() == null) {
                continue;
            }
            TableBill clone = cloneBill(source, targetMenuId, targetTableId, productIdsBySourceId, shift);
            if (clone == null) {
                continue;
            }
            tableBillRepository.save(clone);
            copyPayments(source.getId(), clone, shift);
            created++;
        }
        return created;
    }

    private TableBill cloneBill(
            TableBill source,
            Long targetMenuId,
            Long targetTableId,
            Map<Long, Long> productIdsBySourceId,
            Duration shift
    ) {
        LocalDateTime closedAt = source.getClosedAt().plus(shift);
        LocalDateTime openedAt = source.getOpenedAt() != null
                ? source.getOpenedAt().plus(shift)
                : closedAt.minusMinutes(45);

        List<TableBillItem> mappedItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        if (source.getItems() != null) {
            for (TableBillItem item : source.getItems()) {
                Long targetProductId = productIdsBySourceId.get(item.getProductId());
                if (targetProductId == null) {
                    continue;
                }
                BigDecimal lineTotal = item.getLineTotal() != null ? item.getLineTotal() : BigDecimal.ZERO;
                TableBillItem copy = TableBillItem.builder()
                        .productId(targetProductId)
                        .productName(item.getProductName())
                        .unitPrice(item.getUnitPrice())
                        .quantity(item.getQuantity())
                        .paidQuantity(item.getQuantity())
                        .lineTotal(lineTotal)
                        .note(item.getNote())
                        .createdAt(closedAt)
                        .updatedAt(closedAt)
                        .build();
                mappedItems.add(copy);
                total = total.add(lineTotal);
            }
        }
        if (mappedItems.isEmpty()) {
            return null;
        }

        TableBill bill = TableBill.builder()
                .menuId(targetMenuId)
                .tableId(targetTableId)
                .status(TableBillStatus.CLOSED)
                .openedAt(openedAt)
                .closedAt(closedAt)
                .paymentMethod(source.getPaymentMethod() != null ? source.getPaymentMethod() : TableBillPaymentMethod.CARD)
                .totalAmount(total.setScale(2, RoundingMode.HALF_UP))
                .currency(source.getCurrency() != null ? source.getCurrency() : "TRY")
                .tipAmount(source.getTipAmount())
                .createdAt(openedAt)
                .updatedAt(closedAt)
                .build();
        for (TableBillItem item : mappedItems) {
            bill.addItem(item);
        }
        return bill;
    }

    private void copyPayments(Long sourceBillId, TableBill targetBill, Duration shift) {
        List<BillPayment> payments = billPaymentRepository.findByBillIdOrderByPaidAtAsc(sourceBillId);
        if (payments.isEmpty()) {
            recordFullPayment(targetBill, targetBill.getPaymentMethod(), targetBill.getClosedAt());
            return;
        }
        for (BillPayment payment : payments) {
            LocalDateTime paidAt = payment.getPaidAt() != null
                    ? payment.getPaidAt().plus(shift)
                    : targetBill.getClosedAt();
            BillPayment copy = BillPayment.builder()
                    .bill(targetBill)
                    .paymentMethod(payment.getPaymentMethod())
                    .amount(payment.getAmount())
                    .quantityPaid(payment.getQuantityPaid())
                    .tip(payment.isTip())
                    .splitShareNumber(payment.getSplitShareNumber())
                    .splitPersonCount(payment.getSplitPersonCount())
                    .paidAt(paidAt)
                    .createdAt(paidAt)
                    .build();
            billPaymentRepository.save(copy);
        }
    }

    private void recordFullPayment(TableBill bill, TableBillPaymentMethod method, LocalDateTime paidAt) {
        if (method == null) {
            method = TableBillPaymentMethod.CARD;
        }
        BillPayment payment = BillPayment.builder()
                .bill(bill)
                .paymentMethod(method)
                .amount(bill.getTotalAmount())
                .quantityPaid(0)
                .tip(false)
                .paidAt(paidAt)
                .createdAt(paidAt)
                .build();
        billPaymentRepository.save(payment);
        if (bill.getTipAmount() != null && bill.getTipAmount().compareTo(BigDecimal.ZERO) > 0) {
            billPaymentRepository.save(BillPayment.builder()
                    .bill(bill)
                    .paymentMethod(method)
                    .amount(bill.getTipAmount())
                    .quantityPaid(0)
                    .tip(true)
                    .paidAt(paidAt)
                    .createdAt(paidAt)
                    .build());
        }
    }

    private int synthesizeBills(
            Long targetMenuId,
            Long targetTableId,
            Long userId,
            LocalDateTime from,
            LocalDateTime now,
            int maxBills
    ) {
        List<MenuProduct> products = menuProductRepository
                .findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(targetMenuId);
        if (products.isEmpty()) {
            return 0;
        }
        Random random = new Random(userId != null ? userId : 1L);
        int days = Math.max(1, (int) Duration.between(from, now).toDays());
        int created = 0;
        for (int day = 0; day < days && created < maxBills; day++) {
            int billsToday = 2 + random.nextInt(4);
            for (int i = 0; i < billsToday && created < maxBills; i++) {
                LocalDateTime closedAt = from.plusDays(day)
                        .withHour(11 + random.nextInt(11))
                        .withMinute(random.nextInt(60));
                if (closedAt.isAfter(now)) {
                    continue;
                }
                TableBill bill = buildSyntheticBill(
                        targetMenuId,
                        targetTableId,
                        products,
                        random,
                        closedAt
                );
                tableBillRepository.save(bill);
                recordFullPayment(bill, bill.getPaymentMethod(), closedAt);
                created++;
            }
        }
        return created;
    }

    private TableBill buildSyntheticBill(
            Long menuId,
            Long tableId,
            List<MenuProduct> products,
            Random random,
            LocalDateTime closedAt
    ) {
        int lineCount = 1 + random.nextInt(Math.min(4, products.size()));
        TableBill bill = TableBill.builder()
                .menuId(menuId)
                .tableId(tableId)
                .status(TableBillStatus.CLOSED)
                .openedAt(closedAt.minusMinutes(20 + random.nextInt(40)))
                .closedAt(closedAt)
                .paymentMethod(random.nextBoolean() ? TableBillPaymentMethod.CARD : TableBillPaymentMethod.CASH)
                .currency(products.get(0).getCurrency() != null ? products.get(0).getCurrency() : "TRY")
                .createdAt(closedAt.minusMinutes(30))
                .updatedAt(closedAt)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < lineCount; i++) {
            MenuProduct product = products.get(random.nextInt(products.size()));
            int qty = 1 + random.nextInt(2);
            BigDecimal unit = product.getPrice() != null ? product.getPrice() : BigDecimal.TEN;
            BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
            TableBillItem item = TableBillItem.builder()
                    .productId(product.getProductId())
                    .productName(product.getName())
                    .unitPrice(unit)
                    .quantity(qty)
                    .paidQuantity(qty)
                    .lineTotal(lineTotal)
                    .createdAt(closedAt)
                    .updatedAt(closedAt)
                    .build();
            bill.addItem(item);
            total = total.add(lineTotal);
        }
        bill.setTotalAmount(total.setScale(2, RoundingMode.HALF_UP));
        return bill;
    }
}
