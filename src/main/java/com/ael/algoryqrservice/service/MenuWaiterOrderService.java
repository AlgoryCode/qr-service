package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.MainCategory;
import com.ael.algoryqrservice.model.MenuOrder;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.MenuWaiter;
import com.ael.algoryqrservice.model.RestaurantTable;
import com.ael.algoryqrservice.model.SubCategory;
import com.ael.algoryqrservice.model.TableBill;
import com.ael.algoryqrservice.model.dto.MenuOrderDtos;
import com.ael.algoryqrservice.model.dto.MenuWaiterDtos;
import com.ael.algoryqrservice.model.enums.MenuOrderStatus;
import com.ael.algoryqrservice.model.enums.TableBillStatus;
import com.ael.algoryqrservice.repository.MenuOrderRepository;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuWaiterRepository;
import com.ael.algoryqrservice.repository.RestaurantTableRepository;
import com.ael.algoryqrservice.service.campaign.CampaignEvaluationService;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuWaiterOrderService {

    private static final ZoneId MENU_ZONE = ZoneId.of("Europe/Istanbul");

    private final MenuOrderRepository menuOrderRepository;
    private final MenuWaiterRepository menuWaiterRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final MenuProductRepository menuProductRepository;
    private final MenuOrderService menuOrderService;
    private final MenuTaxonomyService menuTaxonomyService;
    private final TableBillService tableBillService;
    private final WaiterCommissionService waiterCommissionService;
    private final UserAccountingService userAccountingService;
    private final CampaignEvaluationService campaignEvaluationService;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public List<MenuOrderDtos.OrderResponse> listPending(Long menuId) {
        MenuWaiter waiter = requireWaiterForMenu(menuId);
        return menuOrderRepository
                .findByMenuIdAndStatusOrderBySubmittedAtDesc(waiter.getMenuId(), MenuOrderStatus.SUBMITTED)
                .stream()
                .map(menuOrderService::toOrderResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MenuWaiterDtos.TableOrderSummary> listTables(Long menuId) {
        MenuWaiter waiter = requireWaiterForMenu(menuId);
        List<RestaurantTable> tables = restaurantTableRepository
                .findByMenuIdOrderByTableNumberAscNameAsc(waiter.getMenuId());

        List<MenuOrder> pendingOrders = menuOrderRepository
                .findByMenuIdAndStatusOrderBySubmittedAtDesc(waiter.getMenuId(), MenuOrderStatus.SUBMITTED);

        Map<Long, List<MenuOrder>> pendingByTable = pendingOrders.stream()
                .collect(Collectors.groupingBy(MenuOrder::getTableId));

        Map<Long, TableBill> openBillsByTable = tableBillService.findOpenBillsByMenuId(waiter.getMenuId());

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

                    return MenuWaiterDtos.TableOrderSummary.builder()
                            .tableId(table.getId())
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
    public List<MenuOrderDtos.OrderResponse> listTodayHistory(Long menuId) {
        MenuWaiter waiter = requireWaiterForMenu(menuId);
        LocalDateTime[] dayRange = todayRange();
        return menuOrderRepository.findByMenuIdAndStatusInAndSubmittedAtBetweenOrderBySubmittedAtDesc(
                        waiter.getMenuId(),
                        EnumSet.of(
                                MenuOrderStatus.SUBMITTED,
                                MenuOrderStatus.CONFIRMED,
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
        MenuWaiter waiter = requireCurrentWaiter();
        MenuOrder order = requireOrderForWaiterMenu(orderId, waiter);
        if (order.getStatus() != MenuOrderStatus.SUBMITTED) {
            throw new BadRequestException("Sadece gönderilmiş siparişler onaylanabilir");
        }
        order.setStatus(MenuOrderStatus.CONFIRMED);
        order.setConfirmedAt(LocalDateTime.now());
        order.setWaiterId(waiter.getId());
        order.setUpdatedAt(LocalDateTime.now());

        TableBill bill = tableBillService.getOrOpenBill(waiter.getMenuId(), order.getTableId(), waiter.getId());
        order.setBillId(bill.getId());
        MenuOrder saved = menuOrderRepository.save(order);
        tableBillService.addItemsFromOrder(bill, saved, waiter.getId());
        waiterCommissionService.recordOrderCommissions(waiter, saved, bill.getId());
        menuOrderRepository.save(saved);
        userAccountingService.recordConfirmedOrderIncome(saved);
        campaignEvaluationService.onOrderConfirmed(saved);
        return menuOrderService.toOrderResponse(saved);
    }

    @Transactional
    public MenuOrderDtos.OrderResponse reject(Long orderId) {
        MenuWaiter waiter = requireCurrentWaiter();
        MenuOrder order = requireOrderForWaiterMenu(orderId, waiter);
        if (order.getStatus() != MenuOrderStatus.SUBMITTED) {
            throw new BadRequestException("Sadece gönderilmiş siparişler reddedilebilir");
        }
        order.setStatus(MenuOrderStatus.REJECTED);
        order.setRejectedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return menuOrderService.toOrderResponse(menuOrderRepository.save(order));
    }

    @Transactional
    public MenuOrderDtos.OrderResponse cancel(Long orderId) {
        MenuWaiter waiter = requireCurrentWaiter();
        MenuOrder order = requireOrderForWaiterMenu(orderId, waiter);
        if (order.getStatus() != MenuOrderStatus.CONFIRMED && order.getStatus() != MenuOrderStatus.SUBMITTED) {
            throw new BadRequestException("Bu sipariş iptal edilemez");
        }
        order.setStatus(MenuOrderStatus.CANCELLED);
        order.setRejectedAt(LocalDateTime.now());
        order.setWaiterId(waiter.getId());
        order.setUpdatedAt(LocalDateTime.now());
        return menuOrderService.toOrderResponse(menuOrderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public MenuOrderDtos.OrderResponse getOrder(Long orderId) {
        MenuWaiter waiter = requireCurrentWaiter();
        return menuOrderService.toOrderResponse(requireOrderForWaiterMenu(orderId, waiter));
    }

    @Transactional
    public MenuOrderDtos.OrderResponse updateWaiterNote(Long orderId, String note) {
        MenuWaiter waiter = requireCurrentWaiter();
        MenuOrder order = requireOrderForWaiterMenu(orderId, waiter);
        order.setWaiterNote(trimToNull(note));
        order.setUpdatedAt(LocalDateTime.now());
        return menuOrderService.toOrderResponse(menuOrderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public MenuWaiterDtos.CatalogResponse listCatalog() {
        MenuWaiter waiter = requireCurrentWaiter();
        Map<Long, SubCategory> subMap = menuTaxonomyService.loadSubCategoryMap();
        Map<Long, MainCategory> mainMap = menuTaxonomyService.loadMainCategoryMap();

        List<MenuWaiterDtos.CatalogProduct> products = menuProductRepository
                .findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(waiter.getMenuId())
                .stream()
                .map(product -> toCatalogProduct(product, subMap, mainMap))
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
        MenuWaiter waiter = requireCurrentWaiter();
        return menuOrderService.placeWaiterOrder(waiter.getMenuId(), waiter.getId(), request);
    }

    @Transactional(readOnly = true)
    public List<MenuOrderDtos.OrderResponse> getTableTodayOrders(Long tableId) {
        MenuWaiter waiter = requireCurrentWaiter();
        RestaurantTable table = restaurantTableRepository.findByIdAndMenuId(tableId, waiter.getMenuId())
                .orElseThrow(() -> new NotFoundException("Masa bulunamadı"));

        LocalDateTime[] dayRange = todayRange();
        return menuOrderRepository
                .findByTableIdAndSubmittedAtBetweenOrderBySubmittedAtDesc(table.getId(), dayRange[0], dayRange[1])
                .stream()
                .map(menuOrderService::toOrderResponse)
                .toList();
    }

    private MenuWaiter requireWaiterForMenu(Long menuId) {
        if (menuId == null) {
            throw new BadRequestException("menuId zorunludur");
        }
        MenuWaiter waiter = requireCurrentWaiter();
        if (!menuId.equals(waiter.getMenuId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu menüye erişim yetkiniz yok");
        }
        return waiter;
    }

    private MenuWaiter requireCurrentWaiter() {
        Long waiterId = securityUtils.getCurrentWaiterId();
        MenuWaiter waiter = menuWaiterRepository.findById(waiterId)
                .orElseThrow(() -> new NotFoundException("Garson bulunamadı"));
        if (!waiter.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Garson hesabı pasif");
        }
        Long tokenMenuId = securityUtils.getCurrentWaiterMenuId();
        if (!tokenMenuId.equals(waiter.getMenuId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu menüye erişim yetkiniz yok");
        }
        return waiter;
    }

    private MenuOrder requireOrderForWaiterMenu(Long orderId, MenuWaiter waiter) {
        return menuOrderRepository.findByIdAndMenuId(orderId, waiter.getMenuId())
                .orElseThrow(() -> new NotFoundException("Sipariş bulunamadı"));
    }

    private LocalDateTime[] todayRange() {
        LocalDate today = LocalDate.now(MENU_ZONE);
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.atTime(LocalTime.MAX);
        return new LocalDateTime[]{start, end};
    }

    private MenuWaiterDtos.CatalogProduct toCatalogProduct(
            MenuProduct product,
            Map<Long, SubCategory> subMap,
            Map<Long, MainCategory> mainMap
    ) {
        SubCategory sub = product.getSubCategoryId() == null ? null : subMap.get(product.getSubCategoryId());
        MainCategory main = sub == null ? null : mainMap.get(sub.getMainCategoryId());
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
