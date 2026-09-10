package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuCategory;
import com.ael.algoryqrservice.model.MenuOrder;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.MenuSubCategory;
import com.ael.algoryqrservice.model.MenuWaiter;
import com.ael.algoryqrservice.model.RestaurantTable;
import com.ael.algoryqrservice.model.TableBill;
import com.ael.algoryqrservice.model.dto.MenuDtos;
import com.ael.algoryqrservice.model.dto.MenuOrderDtos;
import com.ael.algoryqrservice.model.dto.MenuWaiterDtos;
import com.ael.algoryqrservice.model.enums.CancelReason;
import com.ael.algoryqrservice.model.enums.MenuOrderStatus;
import com.ael.algoryqrservice.model.enums.OrderAuditAction;
import com.ael.algoryqrservice.model.enums.OrderSource;
import com.ael.algoryqrservice.model.enums.TableBillStatus;
import com.ael.algoryqrservice.repository.MenuOrderRepository;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.RestaurantTableRepository;
import com.ael.algoryqrservice.service.campaign.CampaignEvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuWaiterOrderService {

    private static final ZoneId MENU_ZONE = ZoneId.of("Europe/Istanbul");

    private final MenuOrderRepository menuOrderRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final MenuProductRepository menuProductRepository;
    private final MenuOrderService menuOrderService;
    private final MenuCategoryService menuCategoryService;
    private final TableBillService tableBillService;
    private final WaiterCommissionService waiterCommissionService;
    private final CampaignEvaluationService campaignEvaluationService;
    private final MenuProductOptionService menuProductOptionService;
    private final WaiterAccessService waiterAccessService;
    private final OrderAuditService orderAuditService;

    @Transactional(readOnly = true)
    public List<MenuOrderDtos.OrderResponse> listPending() {
        MenuWaiter waiter = waiterAccessService.requireCurrentWaiter();
        List<Long> menuIds = waiterAccessService.menuIdsForWaiter(waiter);
        if (menuIds.isEmpty()) {
            return List.of();
        }
        return menuOrderRepository
                .findByMenuIdInAndStatusOrderBySubmittedAtDesc(menuIds, MenuOrderStatus.SUBMITTED)
                .stream()
                .map(menuOrderService::toOrderResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MenuWaiterDtos.TableOrderSummary> listTables() {
        MenuWaiter waiter = waiterAccessService.requireCurrentWaiter();
        List<Menu> menus = waiterAccessService.menusForWaiter(waiter);
        if (menus.isEmpty()) {
            return List.of();
        }
        List<Long> menuIds = menus.stream().map(Menu::getMenuId).toList();
        Map<Long, Menu> menusById = menus.stream()
                .collect(Collectors.toMap(Menu::getMenuId, Function.identity(), (a, b) -> a));
        boolean showMenuName = menus.size() > 1;

        List<RestaurantTable> tables = restaurantTableRepository
                .findByMenuIdInOrderByTableNumberAscNameAsc(menuIds);

        List<MenuOrder> pendingOrders = menuOrderRepository
                .findByMenuIdInAndStatusOrderBySubmittedAtDesc(menuIds, MenuOrderStatus.SUBMITTED);

        Map<Long, List<MenuOrder>> pendingByTable = pendingOrders.stream()
                .collect(Collectors.groupingBy(MenuOrder::getTableId));

        Map<Long, TableBill> openBillsByTable = tableBillService.findOpenBillsByMenuIds(menuIds);

        return tables.stream()
                .map(table -> {
                    List<MenuOrder> tablePending = pendingByTable.getOrDefault(table.getId(), List.of());
                    MenuOrder latest = tablePending.stream()
                            .max(Comparator.comparing(
                                    MenuOrder::getSubmittedAt,
                                    Comparator.nullsLast(Comparator.naturalOrder())
                            ))
                            .orElse(null);

                    TableBill openBill = openBillsByTable.get(table.getId());
                    TableBillStatus billStatus = openBill != null
                            ? TableBillStatus.OPEN
                            : TableBillStatus.EMPTY;
                    Menu menu = menusById.get(table.getMenuId());

                    return MenuWaiterDtos.TableOrderSummary.builder()
                            .tableId(table.getId())
                            .menuId(table.getMenuId())
                            .menuName(showMenuName && menu != null ? menu.getBusinessName() : null)
                            .tableName(table.getName())
                            .tableNumber(table.getTableNumber())
                            .active(table.isActive())
                            .pendingOrderCount(tablePending.size())
                            .latestPendingOrderId(latest != null ? latest.getId() : null)
                            .latestPendingStatus(latest != null ? latest.getStatus() : null)
                            .latestPendingTotal(latest != null ? latest.getTotalAmount() : null)
                            .latestPendingSubmittedAt(latest != null ? latest.getSubmittedAt() : null)
                            .billStatus(billStatus)
                            .openBillId(openBill != null ? openBill.getId() : null)
                            .openBillTotal(openBill != null ? openBill.getTotalAmount() : null)
                            .openBillItemCount(openBill != null && openBill.getItems() != null
                                    ? openBill.getItems().size()
                                    : 0)
                            .build();
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MenuOrderDtos.OrderResponse> listTodayHistory() {
        MenuWaiter waiter = waiterAccessService.requireCurrentWaiter();
        List<Long> menuIds = waiterAccessService.menuIdsForWaiter(waiter);
        if (menuIds.isEmpty()) {
            return List.of();
        }
        LocalDateTime[] dayRange = todayRange();
        return menuOrderRepository.findByMenuIdInAndStatusInAndSubmittedAtBetweenOrderBySubmittedAtDesc(
                        menuIds,
                        EnumSet.of(
                                MenuOrderStatus.SUBMITTED,
                                MenuOrderStatus.CONFIRMED,
                                MenuOrderStatus.PREPARING,
                                MenuOrderStatus.READY,
                                MenuOrderStatus.SERVED,
                                MenuOrderStatus.REJECTED,
                                MenuOrderStatus.CANCELLED
                        ),
                        dayRange[0],
                        dayRange[1]
                ).stream()
                .map(menuOrderService::toOrderResponse)
                .toList();
    }

    @Transactional
    public MenuOrderDtos.OrderResponse confirm(Long orderId) {
        MenuWaiter waiter = waiterAccessService.requireCurrentWaiter();
        MenuOrder order = requireOrderForWaiter(orderId, waiter);
        if (order.getStatus() != MenuOrderStatus.SUBMITTED) {
            throw new BadRequestException("Sadece gönderilmiş siparişler onaylanabilir");
        }
        order.setStatus(MenuOrderStatus.CONFIRMED);
        order.setConfirmedAt(LocalDateTime.now());
        order.setWaiterId(waiter.getId());
        if (order.getCreatedByWaiterId() == null) {
            order.setCreatedByWaiterId(waiter.getId());
        }
        if (order.getOrderSource() == null) {
            order.setOrderSource(OrderSource.QR);
        }
        order.setUpdatedAt(LocalDateTime.now());

        TableBill bill = tableBillService.getOrOpenBill(order.getMenuId(), order.getTableId(), waiter.getId());
        order.setBillId(bill.getId());
        MenuOrder saved = menuOrderRepository.save(order);
        tableBillService.addItemsFromOrder(bill, saved, waiter.getId());
        waiterCommissionService.recordOrderCommissions(waiter, saved, bill.getId());
        menuOrderRepository.save(saved);
        orderAuditService.record(saved, OrderAuditAction.CONFIRMED, waiter.getId(), null);
        campaignEvaluationService.onOrderConfirmed(saved);
        return menuOrderService.toOrderResponse(saved);
    }

    @Transactional
    public MenuOrderDtos.OrderResponse reject(Long orderId, MenuOrderDtos.CancelOrderRequest request) {
        MenuWaiter waiter = waiterAccessService.requireCurrentWaiter();
        MenuOrder order = requireOrderForWaiter(orderId, waiter);
        if (order.getStatus() != MenuOrderStatus.SUBMITTED) {
            throw new BadRequestException("Sadece gönderilmiş siparişler reddedilebilir");
        }
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(MenuOrderStatus.REJECTED);
        order.setRejectedAt(now);
        order.setCancelledByWaiterId(waiter.getId());
        if (request != null) {
            order.setCancelReason(request.getReason() != null ? request.getReason() : CancelReason.OTHER);
            order.setCancelReasonNote(trimToNull(request.getReasonNote()));
        }
        order.setUpdatedAt(now);
        MenuOrder saved = menuOrderRepository.save(order);
        orderAuditService.record(saved, OrderAuditAction.REJECTED, waiter.getId(), null);
        return menuOrderService.toOrderResponse(saved);
    }

    @Transactional
    public MenuOrderDtos.OrderResponse reject(Long orderId) {
        return reject(orderId, null);
    }

    @Transactional
    public MenuOrderDtos.OrderResponse cancel(Long orderId, MenuOrderDtos.CancelOrderRequest request) {
        MenuWaiter waiter = waiterAccessService.requireCurrentWaiter();
        MenuOrder order = requireOrderForWaiter(orderId, waiter);
        if (!MenuOrderService.isCancellable(order.getStatus())) {
            throw new BadRequestException("Bu sipariş iptal edilemez");
        }
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(MenuOrderStatus.CANCELLED);
        order.setCancelledAt(now);
        order.setRejectedAt(now);
        order.setCancelledByWaiterId(waiter.getId());
        if (request != null) {
            order.setCancelReason(request.getReason() != null ? request.getReason() : CancelReason.OTHER);
            order.setCancelReasonNote(trimToNull(request.getReasonNote()));
        } else {
            order.setCancelReason(CancelReason.OTHER);
        }
        order.setUpdatedAt(now);
        MenuOrder saved = menuOrderRepository.save(order);
        orderAuditService.record(saved, OrderAuditAction.CANCELLED, waiter.getId(), null);
        return menuOrderService.toOrderResponse(saved);
    }

    @Transactional
    public MenuOrderDtos.OrderResponse cancel(Long orderId) {
        return cancel(orderId, null);
    }

    @Transactional
    public MenuOrderDtos.OrderResponse markPreparing(Long orderId) {
        return transitionKitchen(orderId, MenuOrderStatus.CONFIRMED, MenuOrderStatus.PREPARING);
    }

    @Transactional
    public MenuOrderDtos.OrderResponse markReady(Long orderId) {
        return transitionKitchen(orderId, MenuOrderStatus.PREPARING, MenuOrderStatus.READY);
    }

    @Transactional
    public MenuOrderDtos.OrderResponse markServed(Long orderId) {
        return transitionKitchen(orderId, MenuOrderStatus.READY, MenuOrderStatus.SERVED);
    }

    private MenuOrderDtos.OrderResponse transitionKitchen(
            Long orderId,
            MenuOrderStatus expected,
            MenuOrderStatus next
    ) {
        MenuWaiter waiter = waiterAccessService.requireCurrentWaiter();
        MenuOrder order = requireOrderForWaiter(orderId, waiter);
        if (order.getStatus() != expected) {
            throw new BadRequestException("Sipariş durumu bu geçiş için uygun değil: " + order.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(next);
        if (next == MenuOrderStatus.PREPARING) {
            order.setPreparedAt(now);
        } else if (next == MenuOrderStatus.READY) {
            order.setReadyAt(now);
        } else if (next == MenuOrderStatus.SERVED) {
            order.setServedAt(now);
        }
        order.setUpdatedAt(now);
        MenuOrder saved = menuOrderRepository.save(order);
        orderAuditService.record(saved, OrderAuditAction.STATUS_CHANGED, waiter.getId(),
                "{\"from\":\"" + expected + "\",\"to\":\"" + next + "\"}");
        return menuOrderService.toOrderResponse(saved);
    }

    @Transactional(readOnly = true)
    public MenuOrderDtos.OrderResponse getOrder(Long orderId) {
        MenuWaiter waiter = waiterAccessService.requireCurrentWaiter();
        return menuOrderService.toOrderResponse(requireOrderForWaiter(orderId, waiter));
    }

    @Transactional
    public MenuOrderDtos.OrderResponse updateWaiterNote(Long orderId, String note) {
        MenuWaiter waiter = waiterAccessService.requireCurrentWaiter();
        MenuOrder order = requireOrderForWaiter(orderId, waiter);
        order.setWaiterNote(trimToNull(note));
        order.setUpdatedAt(LocalDateTime.now());
        return menuOrderService.toOrderResponse(menuOrderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public MenuWaiterDtos.CatalogResponse listCatalog(Long tableId) {
        MenuWaiter waiter = waiterAccessService.requireCurrentWaiter();
        RestaurantTable table = requireTableForWaiter(tableId, waiter);
        Map<Long, MenuSubCategory> subMap = menuCategoryService.loadSubCategoryMap(table.getMenuId());
        Map<Long, MenuCategory> mainMap = menuCategoryService.loadCategoryMap(table.getMenuId());

        List<MenuProduct> menuProducts = menuProductRepository
                .findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(table.getMenuId());
        Map<Long, List<MenuDtos.MenuProductOptionGroupResponse>> optionsByProduct =
                menuProductOptionService.loadByProductIds(
                        menuProducts.stream().map(MenuProduct::getProductId).toList()
                );

        List<MenuWaiterDtos.CatalogProduct> products = menuProducts.stream()
                .map(product -> toCatalogProduct(product, subMap, mainMap, optionsByProduct))
                .toList();

        return MenuWaiterDtos.CatalogResponse.builder()
                .products(products)
                .commissionEnabled(waiter.isCommissionEnabled())
                .commissionType(waiter.getCommissionType())
                .commissionValue(waiter.getCommissionValue())
                .build();
    }

    @Transactional
    public MenuOrderDtos.OrderResponse createOrder(MenuOrderDtos.WaiterCreateOrderRequest request) {
        MenuWaiter waiter = waiterAccessService.requireCurrentWaiter();
        if (request == null || request.getTableId() == null) {
            throw new BadRequestException("Masa zorunludur");
        }
        RestaurantTable table = requireTableForWaiter(request.getTableId(), waiter);
        return menuOrderService.placeWaiterOrder(table.getMenuId(), waiter.getId(), request);
    }

    @Transactional(readOnly = true)
    public List<MenuOrderDtos.OrderResponse> getTableTodayOrders(Long tableId) {
        MenuWaiter waiter = waiterAccessService.requireCurrentWaiter();
        RestaurantTable table = requireTableForWaiter(tableId, waiter);

        LocalDateTime[] dayRange = todayRange();
        return menuOrderRepository
                .findByTableIdAndSubmittedAtBetweenOrderBySubmittedAtDesc(table.getId(), dayRange[0], dayRange[1])
                .stream()
                .map(menuOrderService::toOrderResponse)
                .toList();
    }

    private RestaurantTable requireTableForWaiter(Long tableId, MenuWaiter waiter) {
        RestaurantTable table = restaurantTableRepository.findById(tableId)
                .orElseThrow(() -> new NotFoundException("Masa bulunamadı"));
        waiterAccessService.requireMenuInWaiterBranch(table.getMenuId(), waiter);
        return table;
    }

    private MenuOrder requireOrderForWaiter(Long orderId, MenuWaiter waiter) {
        MenuOrder order = menuOrderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Sipariş bulunamadı"));
        waiterAccessService.requireMenuInWaiterBranch(order.getMenuId(), waiter);
        return order;
    }

    private LocalDateTime[] todayRange() {
        LocalDate today = LocalDate.now(MENU_ZONE);
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.atTime(LocalTime.MAX);
        return new LocalDateTime[]{start, end};
    }

    private MenuWaiterDtos.CatalogProduct toCatalogProduct(
            MenuProduct product,
            Map<Long, MenuSubCategory> subMap,
            Map<Long, MenuCategory> mainMap,
            Map<Long, List<MenuDtos.MenuProductOptionGroupResponse>> optionsByProduct
    ) {
        MenuSubCategory sub = product.getSubCategoryId() == null ? null : subMap.get(product.getSubCategoryId());
        MenuCategory main = sub == null ? null : mainMap.get(sub.getMenuCategoryId());
        boolean commissionEligible = waiterCommissionService.isCommissionEligible(product, subMap);
        return MenuWaiterDtos.CatalogProduct.builder()
                .productId(product.getProductId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .currency(product.getCurrency())
                .imageUrl(product.getImageUrl())
                .available(product.isAvailable())
                .subCategoryId(product.getSubCategoryId())
                .subCategorySlug(sub == null ? null : sub.getSlug())
                .subCategoryName(sub == null ? null : sub.getName())
                .mainCategoryId(main == null ? null : main.getId())
                .mainCategoryName(main == null ? null : main.getName())
                .commissionEligible(commissionEligible)
                .optionGroups(optionsByProduct.getOrDefault(product.getProductId(), List.of()))
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
