package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.Customer;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuOrder;
import com.ael.algoryqrservice.model.MenuOrderItem;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.MenuWaiter;
import com.ael.algoryqrservice.model.RestaurantTable;
import com.ael.algoryqrservice.model.TableBill;
import com.ael.algoryqrservice.model.TableSession;
import com.ael.algoryqrservice.model.dto.MenuOrderDtos;
import com.ael.algoryqrservice.model.enums.MenuOrderStatus;
import com.ael.algoryqrservice.repository.CustomerRepository;
import com.ael.algoryqrservice.repository.MenuOrderRepository;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.MenuWaiterRepository;
import com.ael.algoryqrservice.repository.RestaurantTableRepository;
import com.ael.algoryqrservice.service.campaign.CampaignEvaluationService;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuOrderService {

    private final MenuOrderRepository menuOrderRepository;
    private final MenuProductRepository menuProductRepository;
    private final MenuRepository menuRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final MenuWaiterRepository menuWaiterRepository;
    private final CustomerRepository customerRepository;
    private final TableSessionService tableSessionService;
    private final TableBillService tableBillService;
    private final WaiterCommissionService waiterCommissionService;
    private final CampaignEvaluationService campaignEvaluationService;
    private final SecurityUtils securityUtils;

    @Transactional
    public MenuOrderDtos.OrderResponse getOrCreateDraft(String tableSessionToken) {
        TableSession session = tableSessionService.requireActiveSession(tableSessionToken);
        return toOrderResponse(getOrCreateDraftEntity(session));
    }

    @Transactional
    public MenuOrderDtos.OrderResponse getCart(Long qrId, String tableSessionToken) {
        TableSession session = requireSessionForQr(qrId, tableSessionToken);
        return toOrderResponse(getOrCreateDraftEntity(session));
    }

    @Transactional
    public MenuOrderDtos.OrderResponse upsertCart(
            Long qrId,
            String tableSessionToken,
            MenuOrderDtos.UpdateCartRequest request
    ) {
        if (request == null || request.getItems() == null) {
            throw new BadRequestException("Sepet kalemleri zorunludur");
        }

        TableSession session = requireSessionForQr(qrId, tableSessionToken);
        MenuOrder order = getOrCreateDraftEntity(session);
        applyCartItems(order, session.getMenuId(), request.getItems());
        order.setNote(trimToNull(request.getNote()));
        order.setUpdatedAt(LocalDateTime.now());
        return toOrderResponse(menuOrderRepository.save(order));
    }

    @Transactional
    public MenuOrderDtos.OrderResponse placeWaiterOrder(
            Long menuId,
            Long waiterId,
            MenuOrderDtos.WaiterCreateOrderRequest request
    ) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new BadRequestException("Sipariş kalemleri zorunludur");
        }

        RestaurantTable table = restaurantTableRepository.findByIdAndMenuId(request.getTableId(), menuId)
                .orElseThrow(() -> new NotFoundException("Masa bulunamadı"));
        if (!table.isActive()) {
            throw new BadRequestException("Masa aktif değil");
        }

        MenuWaiter waiter = menuWaiterRepository.findById(waiterId)
                .orElseThrow(() -> new NotFoundException("Garson bulunamadı"));

        TableBill bill = tableBillService.getOrOpenBill(menuId, table.getId(), waiterId);
        TableSession session = tableBillService.resolveBillSession(bill);

        LocalDateTime now = LocalDateTime.now();
        MenuOrder order = MenuOrder.builder()
                .menuId(menuId)
                .tableId(table.getId())
                .tableSessionId(session.getId())
                .billId(bill.getId())
                .status(MenuOrderStatus.CONFIRMED)
                .waiterId(waiterId)
                .note(trimToNull(request.getNote()))
                .waiterNote(trimToNull(request.getWaiterNote()))
                .totalAmount(BigDecimal.ZERO)
                .currency("TRY")
                .submittedAt(now)
                .confirmedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .items(new ArrayList<>())
                .build();

        applyCartItems(order, menuId, request.getItems());
        MenuOrder saved = menuOrderRepository.save(order);
        tableBillService.addItemsFromOrder(bill, saved, waiterId);
        waiterCommissionService.recordOrderCommissions(waiter, saved, bill.getId());
        menuOrderRepository.save(saved);
        campaignEvaluationService.onOrderConfirmed(saved);
        return toOrderResponse(saved);
    }

    @Transactional
    public MenuOrderDtos.OrderResponse submit(Long qrId, String tableSessionToken) {
        TableSession session = requireSessionForQr(qrId, tableSessionToken);
        MenuOrder order = menuOrderRepository
                .findByTableSessionIdAndStatus(session.getId(), MenuOrderStatus.DRAFT)
                .orElseThrow(() -> new BadRequestException("Gönderilecek sepet bulunamadı"));

        if (order.getItems() == null || order.getItems().isEmpty()) {
            throw new BadRequestException("Sepet boş olamaz");
        }

        order.setStatus(MenuOrderStatus.SUBMITTED);
        order.setSubmittedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        securityUtils.findCurrentCustomerId().ifPresent(order::setCustomerId);

        return toOrderResponse(menuOrderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public MenuOrderDtos.OrderResponse getOrder(Long qrId, String tableSessionToken, Long orderId) {
        TableSession session = requireSessionForQr(qrId, tableSessionToken);
        MenuOrder order = menuOrderRepository.findByIdAndTableSessionId(orderId, session.getId())
                .orElseThrow(() -> new NotFoundException("Sipariş bulunamadı"));
        return toOrderResponse(order);
    }

    @Transactional(readOnly = true)
    public List<MenuOrderDtos.OrderResponse> merchantList(Long menuId, String status) {
        requireOwnedMenu(menuId);
        MenuOrderStatus orderStatus = parseStatus(status, MenuOrderStatus.SUBMITTED);
        return menuOrderRepository.findByMenuIdAndStatusOrderBySubmittedAtDesc(menuId, orderStatus).stream()
                .map(this::toOrderResponse)
                .toList();
    }

    @Transactional
    public MenuOrderDtos.OrderResponse merchantConfirm(Long menuId, Long orderId) {
        requireOwnedMenu(menuId);
        MenuOrder order = requireOrderForMenu(menuId, orderId);
        if (order.getStatus() != MenuOrderStatus.SUBMITTED) {
            throw new BadRequestException("Sadece gönderilmiş siparişler onaylanabilir");
        }
        order.setStatus(MenuOrderStatus.CONFIRMED);
        order.setConfirmedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        TableBill bill = tableBillService.getOrOpenBill(menuId, order.getTableId(), null);
        order.setBillId(bill.getId());
        MenuOrder saved = menuOrderRepository.save(order);
        tableBillService.addItemsFromOrder(bill, saved, null);
        campaignEvaluationService.onOrderConfirmed(saved);
        return toOrderResponse(saved);
    }

    @Transactional
    public MenuOrderDtos.OrderResponse merchantReject(Long menuId, Long orderId) {
        requireOwnedMenu(menuId);
        MenuOrder order = requireOrderForMenu(menuId, orderId);
        if (order.getStatus() != MenuOrderStatus.SUBMITTED) {
            throw new BadRequestException("Sadece gönderilmiş siparişler reddedilebilir");
        }
        order.setStatus(MenuOrderStatus.REJECTED);
        order.setRejectedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return toOrderResponse(menuOrderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public List<MenuOrderDtos.OrderResponse> customerList(Long menuId) {
        if (menuId == null) {
            throw new BadRequestException("menuId zorunludur");
        }
        Long customerId = securityUtils.getCurrentCustomerId();
        return menuOrderRepository.findByCustomerIdAndMenuIdOrderByCreatedAtDesc(customerId, menuId).stream()
                .map(this::toOrderResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public MenuOrderDtos.OrderResponse customerGet(Long orderId) {
        Long customerId = securityUtils.getCurrentCustomerId();
        MenuOrder order = menuOrderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new NotFoundException("Sipariş bulunamadı"));
        return toOrderResponse(order);
    }

    private void applyCartItems(MenuOrder order, Long menuId, List<MenuOrderDtos.CartItemRequest> items) {
        order.clearItems();
        BigDecimal total = BigDecimal.ZERO;
        String currency = "TRY";

        if (items != null && !items.isEmpty()) {
            Map<Long, MenuProduct> productsById = loadProductsForMenu(menuId, items);

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
                total = total.add(lineTotal);

                if (product.getCurrency() != null && !product.getCurrency().isBlank()) {
                    currency = product.getCurrency();
                }

                MenuOrderItem item = MenuOrderItem.builder()
                        .productId(product.getProductId())
                        .productName(product.getName())
                        .unitPrice(unitPrice)
                        .quantity(quantity)
                        .note(trimToNull(itemRequest.getNote()))
                        .lineTotal(lineTotal)
                        .build();
                order.addItem(item);
            }
        }

        order.setTotalAmount(total);
        order.setCurrency(currency);
    }

    private MenuOrder getOrCreateDraftEntity(TableSession session) {
        return menuOrderRepository
                .findByTableSessionIdAndStatus(session.getId(), MenuOrderStatus.DRAFT)
                .orElseGet(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    MenuOrder draft = MenuOrder.builder()
                            .menuId(session.getMenuId())
                            .tableId(session.getTableId())
                            .tableSessionId(session.getId())
                            .status(MenuOrderStatus.DRAFT)
                            .totalAmount(BigDecimal.ZERO)
                            .currency("TRY")
                            .createdAt(now)
                            .updatedAt(now)
                            .items(new ArrayList<>())
                            .build();
                    return menuOrderRepository.save(draft);
                });
    }

    private TableSession requireSessionForQr(Long qrId, String tableSessionToken) {
        TableSession session = tableSessionService.requireActiveSession(tableSessionToken);
        Menu menu = menuRepository.findByQrIdAndActiveTrueAndDeletedFalse(qrId)
                .orElseThrow(() -> new NotFoundException("Menü bulunamadı"));
        if (!session.getMenuId().equals(menu.getMenuId())) {
            throw new BadRequestException("Masa oturumu bu menüye ait değil");
        }
        return session;
    }

    private Menu requireOwnedMenu(Long menuId) {
        Menu menu = menuRepository.findById(menuId)
                .filter(m -> !m.isDeleted())
                .orElseThrow(() -> new NotFoundException("Menü bulunamadı"));
        Long currentUserId = securityUtils.getCurrentUserId();
        if (!currentUserId.equals(menu.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu menüye erişim yetkiniz yok");
        }
        return menu;
    }

    private MenuOrder requireOrderForMenu(Long menuId, Long orderId) {
        return menuOrderRepository.findByIdAndMenuId(orderId, menuId)
                .orElseThrow(() -> new NotFoundException("Sipariş bulunamadı"));
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

    private MenuOrderStatus parseStatus(String status, MenuOrderStatus defaultStatus) {
        if (status == null || status.isBlank()) {
            return defaultStatus;
        }
        try {
            return MenuOrderStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Geçersiz sipariş durumu: " + status);
        }
    }

    public MenuOrderDtos.OrderResponse toOrderResponse(MenuOrder order) {
        String tableName = restaurantTableRepository.findById(order.getTableId())
                .map(RestaurantTable::getName)
                .orElse(null);

        String waiterName = null;
        if (order.getWaiterId() != null) {
            waiterName = menuWaiterRepository.findById(order.getWaiterId())
                    .map(MenuWaiter::getDisplayName)
                    .orElse(null);
        }

        String customerName = null;
        String customerEmail = null;
        if (order.getCustomerId() != null) {
            Customer customer = customerRepository.findById(order.getCustomerId()).orElse(null);
            if (customer != null) {
                String displayName = customer.getDisplayName();
                customerName = displayName == null || displayName.isBlank() ? null : displayName;
                customerEmail = customer.getEmail();
            }
        }

        List<MenuOrderDtos.OrderItemResponse> items = order.getItems() == null
                ? List.of()
                : order.getItems().stream()
                .map(item -> MenuOrderDtos.OrderItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .unitPrice(item.getUnitPrice())
                        .quantity(item.getQuantity())
                        .note(item.getNote())
                        .lineTotal(item.getLineTotal())
                        .build())
                .toList();

        return MenuOrderDtos.OrderResponse.builder()
                .id(order.getId())
                .menuId(order.getMenuId())
                .tableId(order.getTableId())
                .tableName(tableName)
                .tableSessionId(order.getTableSessionId())
                .customerId(order.getCustomerId())
                .customerName(customerName)
                .customerEmail(customerEmail)
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .currency(order.getCurrency())
                .note(order.getNote())
                .waiterId(order.getWaiterId())
                .waiterName(waiterName)
                .waiterNote(order.getWaiterNote())
                .billId(order.getBillId())
                .commissionAmount(order.getCommissionAmount())
                .items(items)
                .submittedAt(order.getSubmittedAt())
                .confirmedAt(order.getConfirmedAt())
                .rejectedAt(order.getRejectedAt())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .campaignSummary(campaignEvaluationService.summarizeOrder(order))
                .build();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
