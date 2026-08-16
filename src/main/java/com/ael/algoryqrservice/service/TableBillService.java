package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuOrder;
import com.ael.algoryqrservice.model.MenuOrderItem;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.MenuWaiter;
import com.ael.algoryqrservice.model.RestaurantTable;
import com.ael.algoryqrservice.model.TableBill;
import com.ael.algoryqrservice.model.TableBillItem;
import com.ael.algoryqrservice.model.TableSession;
import com.ael.algoryqrservice.model.dto.MenuOrderDtos;
import com.ael.algoryqrservice.model.dto.TableBillDtos;
import com.ael.algoryqrservice.model.enums.TableBillPaymentMethod;
import com.ael.algoryqrservice.model.enums.TableBillStatus;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.MenuWaiterRepository;
import com.ael.algoryqrservice.repository.RestaurantTableRepository;
import com.ael.algoryqrservice.repository.TableBillItemRepository;
import com.ael.algoryqrservice.repository.TableBillRepository;
import com.ael.algoryqrservice.repository.TableSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TableBillService {

    private final TableBillRepository tableBillRepository;
    private final TableBillItemRepository tableBillItemRepository;
    private final TableSessionRepository tableSessionRepository;
    private final TableSessionService tableSessionService;
    private final MenuProductRepository menuProductRepository;
    private final MenuRepository menuRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final MenuWaiterRepository menuWaiterRepository;
    private final WaiterCommissionService waiterCommissionService;
    private final UserAccountingService userAccountingService;

    @Transactional
    public TableBill getOrOpenBill(Long menuId, Long tableId, Long waiterId) {
        return tableBillRepository.findByMenuIdAndTableIdAndStatus(menuId, tableId, TableBillStatus.OPEN)
                .orElseGet(() -> openBill(menuId, tableId, waiterId));
    }

    @Transactional
    public TableBill openBill(Long menuId, Long tableId, Long waiterId) {
        RestaurantTable table = restaurantTableRepository.findByIdAndMenuId(tableId, menuId)
                .orElseThrow(() -> new NotFoundException("Masa bulunamadı"));
        if (!table.isActive()) {
            throw new BadRequestException("Masa aktif değil");
        }

        tableBillRepository.findByMenuIdAndTableIdAndStatus(menuId, tableId, TableBillStatus.OPEN)
                .ifPresent(existing -> {
                    throw new BadRequestException("Bu masa için zaten açık bir adisyon var");
                });

        TableSession session = tableSessionService.openInternalSession(menuId, tableId);
        LocalDateTime now = LocalDateTime.now();

        TableBill bill = TableBill.builder()
                .menuId(menuId)
                .tableId(tableId)
                .tableSessionId(session.getId())
                .status(TableBillStatus.OPEN)
                .openedByWaiterId(waiterId)
                .openedAt(now)
                .totalAmount(BigDecimal.ZERO)
                .currency("TRY")
                .createdAt(now)
                .updatedAt(now)
                .build();

        return tableBillRepository.save(bill);
    }

    @Transactional
    public TableBill addItemsFromOrder(TableBill bill, MenuOrder order, Long waiterId) {
        if (bill.getStatus() != TableBillStatus.OPEN) {
            throw new BadRequestException("Kapalı adisyona kalem eklenemez");
        }
        if (order.getItems() == null || order.getItems().isEmpty()) {
            return bill;
        }

        LocalDateTime now = LocalDateTime.now();
        for (MenuOrderItem orderItem : order.getItems()) {
            TableBillItem billItem = TableBillItem.builder()
                    .productId(orderItem.getProductId())
                    .productName(orderItem.getProductName())
                    .unitPrice(orderItem.getUnitPrice())
                    .quantity(orderItem.getQuantity())
                    .lineTotal(orderItem.getLineTotal())
                    .note(orderItem.getNote())
                    .sourceOrderId(order.getId())
                    .addedByWaiterId(waiterId)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            bill.addItem(billItem);
        }

        recalculateTotal(bill);
        bill.setUpdatedAt(now);
        return tableBillRepository.save(bill);
    }

    @Transactional
    public TableBillDtos.BillResponse addItems(
            Long menuId,
            Long billId,
            List<MenuOrderDtos.CartItemRequest> items,
            Long waiterId
    ) {
        TableBill bill = requireOpenBill(menuId, billId);
        List<WaiterCommissionService.CommissionLineItem> addedItems = appendCartItems(
                bill,
                menuId,
                items,
                waiterId,
                null
        );
        recalculateTotal(bill);
        bill.setUpdatedAt(LocalDateTime.now());
        TableBill saved = tableBillRepository.save(bill);
        recordDirectAddCommissions(waiterId, saved, addedItems);
        return toBillResponse(saved, null);
    }

    @Transactional
    public TableBillDtos.BillResponse updateItemQuantity(
            Long menuId,
            Long billId,
            Long itemId,
            int quantity
    ) {
        TableBill bill = requireOpenBill(menuId, billId);
        TableBillItem item = tableBillItemRepository.findByIdAndBillId(itemId, billId)
                .orElseThrow(() -> new NotFoundException("Adisyon kalemi bulunamadı"));

        if (quantity <= 0) {
            bill.removeItem(item);
            tableBillItemRepository.delete(item);
        } else {
            item.setQuantity(quantity);
            item.setLineTotal(item.getUnitPrice().multiply(BigDecimal.valueOf(quantity)));
            item.setUpdatedAt(LocalDateTime.now());
        }

        recalculateTotal(bill);
        bill.setUpdatedAt(LocalDateTime.now());
        return toBillResponse(tableBillRepository.save(bill), null);
    }

    @Transactional
    public TableBillDtos.BillResponse removeItem(Long menuId, Long billId, Long itemId) {
        return updateItemQuantity(menuId, billId, itemId, 0);
    }

    @Transactional
    public TableBillDtos.BillResponse closeBill(
            Long menuId,
            Long billId,
            MenuWaiter waiter,
            TableBillPaymentMethod paymentMethod,
            Boolean tipReceived,
            BigDecimal tipAmount
    ) {
        if (paymentMethod == null) {
            throw new BadRequestException("Ödeme yöntemi seçilmelidir");
        }

        TableBill bill = requireOpenBill(menuId, billId);
        recalculateTotal(bill);

        BigDecimal resolvedTipAmount = resolveTipAmount(tipReceived, tipAmount);

        LocalDateTime now = LocalDateTime.now();
        bill.setStatus(TableBillStatus.CLOSED);
        bill.setClosedByWaiterId(waiter.getId());
        bill.setClosedAt(now);
        bill.setPaymentMethod(paymentMethod);
        bill.setTipAmount(resolvedTipAmount);
        bill.setUpdatedAt(now);

        TableBill saved = tableBillRepository.save(bill);
        revokeSessionsForTable(bill.getTableId());

        Menu menu = menuRepository.findById(menuId).orElse(null);
        userAccountingService.recordBillCloseIncome(menu, saved, waiter, resolvedTipAmount);

        return toBillResponse(saved, null);
    }

    private BigDecimal resolveTipAmount(Boolean tipReceived, BigDecimal tipAmount) {
        if (!Boolean.TRUE.equals(tipReceived)) {
            return null;
        }
        if (tipAmount == null || tipAmount.compareTo(new BigDecimal("0.01")) < 0) {
            throw new BadRequestException("Bahşiş tutarı 0,01 veya daha büyük olmalıdır");
        }
        return tipAmount;
    }

    @Transactional(readOnly = true)
    public TableBillDtos.BillResponse getOpenBillForTable(Long menuId, Long tableId) {
        TableBill bill = tableBillRepository.findByMenuIdAndTableIdAndStatus(menuId, tableId, TableBillStatus.OPEN)
                .orElseThrow(() -> new NotFoundException("Açık adisyon bulunamadı"));
        return toBillResponse(bill, null);
    }

    @Transactional(readOnly = true)
    public TableBillDtos.BillResponse getBill(Long menuId, Long billId) {
        TableBill bill = tableBillRepository.findByIdAndMenuId(billId, menuId)
                .orElseThrow(() -> new NotFoundException("Adisyon bulunamadı"));
        return toBillResponse(bill, null);
    }

    @Transactional(readOnly = true)
    public Map<Long, TableBill> findOpenBillsByMenuId(Long menuId) {
        return tableBillRepository.findByMenuIdAndStatus(menuId, TableBillStatus.OPEN).stream()
                .collect(Collectors.toMap(TableBill::getTableId, bill -> bill, (a, b) -> a));
    }

    public TableBillDtos.BillResponse toBillResponse(TableBill bill, BigDecimal fixedCommissionAmount) {
        String tableName = restaurantTableRepository.findById(bill.getTableId())
                .map(RestaurantTable::getName)
                .orElse(null);

        List<TableBillDtos.BillItemResponse> items = bill.getItems() == null
                ? List.of()
                : bill.getItems().stream()
                .map(item -> TableBillDtos.BillItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .unitPrice(item.getUnitPrice())
                        .quantity(item.getQuantity())
                        .lineTotal(item.getLineTotal())
                        .note(item.getNote())
                        .sourceOrderId(item.getSourceOrderId())
                        .addedByWaiterId(item.getAddedByWaiterId())
                        .createdAt(item.getCreatedAt())
                        .build())
                .toList();

        return TableBillDtos.BillResponse.builder()
                .id(bill.getId())
                .menuId(bill.getMenuId())
                .tableId(bill.getTableId())
                .tableName(tableName)
                .status(bill.getStatus())
                .openedByWaiterId(bill.getOpenedByWaiterId())
                .closedByWaiterId(bill.getClosedByWaiterId())
                .openedAt(bill.getOpenedAt())
                .closedAt(bill.getClosedAt())
                .paymentMethod(bill.getPaymentMethod())
                .totalAmount(bill.getTotalAmount())
                .currency(bill.getCurrency())
                .itemCount(items.size())
                .items(items)
                .fixedCommissionAmount(fixedCommissionAmount)
                .build();
    }

    public TableSession resolveBillSession(TableBill bill) {
        if (bill.getTableSessionId() != null) {
            return tableSessionRepository.findById(bill.getTableSessionId())
                    .filter(TableSession::isActive)
                    .orElseGet(() -> {
                        TableSession session = tableSessionService.openInternalSession(bill.getMenuId(), bill.getTableId());
                        bill.setTableSessionId(session.getId());
                        tableBillRepository.save(bill);
                        return session;
                    });
        }
        TableSession session = tableSessionService.openInternalSession(bill.getMenuId(), bill.getTableId());
        bill.setTableSessionId(session.getId());
        tableBillRepository.save(bill);
        return session;
    }

    private TableBill requireOpenBill(Long menuId, Long billId) {
        return tableBillRepository.findByIdAndMenuIdAndStatus(billId, menuId, TableBillStatus.OPEN)
                .orElseThrow(() -> new NotFoundException("Açık adisyon bulunamadı"));
    }

    private List<WaiterCommissionService.CommissionLineItem> appendCartItems(
            TableBill bill,
            Long menuId,
            List<MenuOrderDtos.CartItemRequest> items,
            Long waiterId,
            Long sourceOrderId
    ) {
        if (items == null || items.isEmpty()) {
            throw new BadRequestException("En az bir kalem gerekli");
        }

        Map<Long, MenuProduct> productsById = loadProductsForMenu(menuId, items);
        LocalDateTime now = LocalDateTime.now();
        List<WaiterCommissionService.CommissionLineItem> addedItems = new ArrayList<>();

        for (MenuOrderDtos.CartItemRequest itemRequest : items) {
            MenuProduct product = productsById.get(itemRequest.getProductId());
            if (product == null) {
                throw new BadRequestException("Ürün bulunamadı: " + itemRequest.getProductId());
            }
            if (!product.isAvailable()) {
                throw new BadRequestException("Ürün şu an siparişe kapalı: " + product.getName());
            }

            BigDecimal unitPrice = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
            int quantity = itemRequest.getQuantity();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));

            if (product.getCurrency() != null && !product.getCurrency().isBlank()) {
                bill.setCurrency(product.getCurrency());
            }

            TableBillItem billItem = TableBillItem.builder()
                    .productId(product.getProductId())
                    .productName(product.getName())
                    .unitPrice(unitPrice)
                    .quantity(quantity)
                    .lineTotal(lineTotal)
                    .note(trimToNull(itemRequest.getNote()))
                    .sourceOrderId(sourceOrderId)
                    .addedByWaiterId(waiterId)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            bill.addItem(billItem);
            addedItems.add(new WaiterCommissionService.CommissionLineItem(product.getProductId(), quantity));
        }

        return addedItems;
    }

    private void recordDirectAddCommissions(
            Long waiterId,
            TableBill bill,
            List<WaiterCommissionService.CommissionLineItem> addedItems
    ) {
        if (addedItems == null || addedItems.isEmpty()) {
            return;
        }
        MenuWaiter waiter = menuWaiterRepository.findById(waiterId).orElse(null);
        if (waiter == null) {
            return;
        }
        waiterCommissionService.recordFixedItemAddCommission(
                waiter,
                bill.getId(),
                null,
                addedItems,
                bill.getCurrency()
        );
    }

    private void recalculateTotal(TableBill bill) {
        BigDecimal total = BigDecimal.ZERO;
        if (bill.getItems() != null) {
            for (TableBillItem item : bill.getItems()) {
                total = total.add(item.getLineTotal() != null ? item.getLineTotal() : BigDecimal.ZERO);
            }
        }
        bill.setTotalAmount(total);
    }

    private void revokeSessionsForTable(Long tableId) {
        List<TableSession> sessions = tableSessionRepository.findByTableIdAndRevokedFalse(tableId);
        for (TableSession session : sessions) {
            session.setRevoked(true);
        }
        tableSessionRepository.saveAll(sessions);
    }

    private Map<Long, MenuProduct> loadProductsForMenu(
            Long menuId,
            List<MenuOrderDtos.CartItemRequest> items
    ) {
        Set<Long> productIds = items.stream()
                .map(MenuOrderDtos.CartItemRequest::getProductId)
                .collect(Collectors.toSet());

        Map<Long, MenuProduct> productsById = new HashMap<>();
        for (Long productId : productIds) {
            MenuProduct product = menuProductRepository.findByProductIdAndDeletedFalse(productId)
                    .filter(p -> menuId.equals(p.getMenuId()))
                    .orElseThrow(() -> new BadRequestException("Ürün bu menüde bulunamadı: " + productId));
            productsById.put(productId, product);
        }
        return productsById;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
