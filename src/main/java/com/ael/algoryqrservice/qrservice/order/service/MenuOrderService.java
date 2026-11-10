package com.ael.algoryqrservice.qrservice.order.service;

import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.customerservice.infrastructure.persistence.entity.Customer;
import com.ael.algoryqrservice.qrservice.model.Menu;
import com.ael.algoryqrservice.qrservice.orderflow.service.OrderFlowLookup;
import com.ael.algoryqrservice.qrservice.model.MenuOrder;
import com.ael.algoryqrservice.qrservice.model.MenuOrderItem;
import com.ael.algoryqrservice.qrservice.product.model.MenuProduct;
import com.ael.algoryqrservice.qrservice.product.model.MenuProductOptionGroup;
import com.ael.algoryqrservice.staffservice.infrastructure.persistence.entity.MerchantStaff;
import com.ael.algoryqrservice.qrservice.model.RestaurantTable;
import com.ael.algoryqrservice.qrservice.product.model.SelectedMenuOption;
import com.ael.algoryqrservice.billservice.infrastructure.persistence.entity.TableBill;
import com.ael.algoryqrservice.qrservice.model.TableSession;
import com.ael.algoryqrservice.qrservice.order.dto.MenuOrderDtos;
import com.ael.algoryqrservice.qrservice.model.enums.MenuOrderStatus;
import com.ael.algoryqrservice.qrservice.model.enums.OrderAuditAction;
import com.ael.algoryqrservice.qrservice.model.enums.OrderSource;
import com.ael.algoryqrservice.qrservice.model.enums.CancelReason;
import com.ael.algoryqrservice.customerservice.infrastructure.persistence.repository.CustomerRepository;
import com.ael.algoryqrservice.qrservice.repository.MenuOrderRepository;
import com.ael.algoryqrservice.qrservice.product.repository.MenuProductRepository;
import com.ael.algoryqrservice.qrservice.repository.MenuRepository;
import com.ael.algoryqrservice.staffservice.infrastructure.persistence.repository.MerchantStaffRepository;
import com.ael.algoryqrservice.qrservice.repository.RestaurantTableRepository;
import com.ael.algoryqrservice.qrservice.campaign.service.CampaignEvaluationService;
import com.ael.algoryqrservice.qrservice.print.service.PrintOrderEnqueueService;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ael.algoryqrservice.qrservice.campaign.dto.CampaignDtos;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import com.ael.algoryqrservice.qrservice.product.service.MenuProductOptionService;
import com.ael.algoryqrservice.qrservice.stock.service.StockConsumptionService;
import com.ael.algoryqrservice.staffservice.application.StaffCommissionService;
import com.ael.algoryqrservice.qrservice.order.service.KitchenUberEatsMapper;
import com.ael.algoryqrservice.qrservice.order.service.KitchenUberEatsService;
import com.ael.algoryqrservice.qrservice.order.service.KitchenYemekSepetiService;
import com.ael.algoryqrservice.qrservice.order.service.OrderAuditService;
import com.ael.algoryqrservice.billservice.application.TableBillService;
import com.ael.algoryqrservice.qrservice.order.service.TableSessionService;

@Service
@RequiredArgsConstructor
public class MenuOrderService {

    private final MenuOrderRepository menuOrderRepository;
    private final MenuProductRepository menuProductRepository;
    private final MenuRepository menuRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final MerchantStaffRepository merchantStaffRepository;
    private final CustomerRepository customerRepository;
    private final TableSessionService tableSessionService;
    private final TableBillService tableBillService;
    private final StaffCommissionService waiterCommissionService;
    private final CampaignEvaluationService campaignEvaluationService;
    private final MenuProductOptionService menuProductOptionService;
    private final OrderAuditService orderAuditService;
    private final SecurityUtils securityUtils;
    private final KitchenUberEatsService kitchenUberEatsService;
    private final KitchenYemekSepetiService kitchenYemekSepetiService;
    private final PrintOrderEnqueueService printOrderEnqueueService;
    private final StockConsumptionService stockConsumptionService;
    private final OrderFlowLookup orderFlowLookup;
    private final KitchenRoutingService kitchenRoutingService;

    @Transactional
    public MenuOrderDtos.OrderResponse getOrCreateDraft(String tableSessionToken) {
        TableSession session = tableSessionService.requireActiveSession(tableSessionToken);
        return toOrderResponse(getOrCreateDraftEntity(session));
    }

    @Transactional
    public MenuOrderDtos.OrderResponse getCart(Long menuId, String tableSessionToken) {
        TableSession session = requireSessionForMenu(menuId, tableSessionToken);
        return toOrderResponse(getOrCreateDraftEntity(session));
    }

    @Transactional
    public MenuOrderDtos.OrderResponse upsertCart(
            Long menuId,
            String tableSessionToken,
            MenuOrderDtos.UpdateCartRequest request
    ) {
        if (request == null || request.getItems() == null) {
            throw new BadRequestException("Sepet kalemleri zorunludur");
        }

        TableSession session = requireSessionForMenu(menuId, tableSessionToken);
        MenuOrder order = getOrCreateDraftEntity(session);
        applyCartItems(order, session.getMenuId(), request.getItems());
        order.setNote(trimToNull(request.getNote()));
        order.setUpdatedAt(LocalDateTime.now());
        return toOrderResponse(menuOrderRepository.save(order));
    }

    @Transactional
    public MenuOrderDtos.OrderResponse placeWaiterOrder(
            Long menuId,
            Long staffId,
            MenuOrderDtos.WaiterCreateOrderRequest request
    ) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new BadRequestException("Sipariş kalemleri zorunludur");
        }

        MerchantStaff waiter = merchantStaffRepository.findById(staffId)
                .orElseThrow(() -> new NotFoundException("Garson bulunamadı"));

        RestaurantTable table = null;
        TableBill bill = null;
        TableSession session = null;
        if (request.getTableId() != null) {
            table = restaurantTableRepository.findByIdAndMenuId(request.getTableId(), menuId)
                    .filter(item -> !item.isDeleted())
                    .orElseThrow(() -> new NotFoundException("Masa bulunamadı"));
            if (!table.isActive()) {
                throw new BadRequestException("Masa aktif değil");
            }
            bill = tableBillService.getOrOpenBill(menuId, table.getId(), staffId);
            if (request.getCoverCount() != null && request.getCoverCount() > 0) {
                bill.setCoverCount(request.getCoverCount());
                tableBillService.saveBill(bill);
            }
            session = tableBillService.resolveBillSession(bill);
        }

        // "Adisyona ekle" her kalemi doğrudan servis edilmiş yazar; "Mutfağa gönder" mutfak açıkken kalemleri mutfağa
        // yollar, "mutfağa gitmez" kategorilerdekiler yine doğrudan adisyona düşer. İki sipariş de aynı adisyondadır.
        Set<Long> productIds = request.getItems().stream()
                .map(MenuOrderDtos.CartItemRequest::getProductId)
                .collect(Collectors.toSet());
        boolean addToBillOnly = Boolean.FALSE.equals(request.getSendToKitchen());
        if (Boolean.TRUE.equals(request.getSendToKitchen()) && !kitchenRoutingService.kitchenEnabled(menuId)) {
            throw new BadRequestException("Mutfak özelliği kapalı; siparişi adisyona ekleyebilirsiniz");
        }
        // Kitchen and "mutfağa gitmez" items always land in separate orders, also on "Adisyona ekle": a later
        // "Mutfağa gönder" on that order must then not drag the drinks into the kitchen.
        Set<Long> directIds = kitchenRoutingService.directProductIds(menuId, productIds);
        List<MenuOrderDtos.CartItemRequest> kitchenItems = request.getItems().stream()
                .filter(item -> !directIds.contains(item.getProductId()))
                .toList();
        List<MenuOrderDtos.CartItemRequest> directItems = request.getItems().stream()
                .filter(item -> directIds.contains(item.getProductId()))
                .toList();

        MenuOrderDtos.OrderResponse kitchenResponse = kitchenItems.isEmpty()
                ? null
                : persistWaiterOrder(menuId, waiter, table, session, bill, kitchenItems, request, addToBillOnly, true);
        MenuOrderDtos.OrderResponse directResponse = directItems.isEmpty()
                ? null
                : persistWaiterOrder(menuId, waiter, table, session, bill, directItems, request, true, kitchenResponse == null);
        return kitchenResponse != null ? kitchenResponse : directResponse;
    }

    /**
     * Garson siparişinin bir parçasını kaydeder. {@code direct} parça mutfağa gitmez: servis edilmiş olarak açılır ve
     * mutfak fişi basılmaz. Sipariş notu yalnızca mutfağa giden parçaya (yoksa doğrudan parçaya) yazılır.
     */
    private MenuOrderDtos.OrderResponse persistWaiterOrder(
            Long menuId,
            MerchantStaff waiter,
            RestaurantTable table,
            TableSession session,
            TableBill bill,
            List<MenuOrderDtos.CartItemRequest> items,
            MenuOrderDtos.WaiterCreateOrderRequest request,
            boolean direct,
            boolean carriesNote
    ) {
        Long staffId = waiter.getId();
        LocalDateTime now = LocalDateTime.now();
        MenuOrder order = MenuOrder.builder()
                .menuId(menuId)
                .tableId(table == null ? null : table.getId())
                .tableSessionId(session == null ? null : session.getId())
                .billId(bill == null ? null : bill.getId())
                .status(direct ? MenuOrderStatus.SERVED : MenuOrderStatus.CONFIRMED)
                .orderSource(OrderSource.WAITER)
                .staffId(staffId)
                .createdByStaffId(staffId)
                .note(carriesNote ? trimToNull(request.getNote()) : null)
                .waiterNote(carriesNote ? trimToNull(request.getWaiterNote()) : null)
                .totalAmount(BigDecimal.ZERO)
                .currency("TRY")
                .submittedAt(now)
                .confirmedAt(now)
                .readyAt(direct ? now : null)
                .servedAt(direct ? now : null)
                .createdAt(now)
                .updatedAt(now)
                .items(new ArrayList<>())
                .build();

        applyCartItems(order, menuId, items);
        MenuOrder saved = menuOrderRepository.save(order);
        stockConsumptionService.consumeMenuOrder(saved);
        if (bill != null) {
            tableBillService.addItemsFromOrder(bill, saved, staffId);
        }
        waiterCommissionService.recordOrderCommissions(waiter, saved, bill == null ? null : bill.getId());
        menuOrderRepository.save(saved);
        orderAuditService.record(saved, OrderAuditAction.CREATED, staffId, direct ? "{\"skipKitchen\":true}" : null);
        campaignEvaluationService.onOrderConfirmed(saved);
        MenuOrderDtos.OrderResponse response = toOrderResponse(saved);
        if (!direct) {
            printOrderEnqueueService.enqueueMenuOrder(saved, response);
        }
        return response;
    }


    @Transactional
    public MenuOrderDtos.OrderResponse submit(Long menuId, String tableSessionToken, UUID analyticsSessionId) {
        TableSession session = requireSessionForMenu(menuId, tableSessionToken);
        MenuOrder order = menuOrderRepository
                .findByTableSessionIdAndStatus(session.getId(), MenuOrderStatus.DRAFT)
                .orElseThrow(() -> new BadRequestException("Gönderilecek sepet bulunamadı"));

        if (order.getItems() == null || order.getItems().isEmpty()) {
            throw new BadRequestException("Sepet boş olamaz");
        }
        Long branchId = menuRepository.findById(order.getMenuId()).map(Menu::getBranchId).orElse(null);
        if (orderFlowLookup.requiresTableApproval(branchId)) {
            return submitForApproval(order, analyticsSessionId);
        }

        LocalDateTime now = LocalDateTime.now();
        order.setStatus(MenuOrderStatus.CONFIRMED);
        order.setOrderSource(OrderSource.QR);
        order.setSubmittedAt(now);
        order.setConfirmedAt(now);
        order.setUpdatedAt(now);
        if (analyticsSessionId != null) {
            order.setAnalyticsSessionId(analyticsSessionId);
        }
        securityUtils.findCurrentCustomerId().ifPresent(order::setCustomerId);

        TableBill bill = tableBillService.getOrOpenBill(order.getMenuId(), order.getTableId(), null);
        order.setBillId(bill.getId());
        MenuOrder saved = menuOrderRepository.save(order);
        stockConsumptionService.consumeMenuOrder(saved);
        tableBillService.addItemsFromOrder(bill, saved, null);
        orderAuditService.record(saved, OrderAuditAction.CREATED, null, "{\"source\":\"QR\"}");
        campaignEvaluationService.onOrderConfirmed(saved);
        MenuOrderDtos.OrderResponse response = toOrderResponse(saved);
        printOrderEnqueueService.enqueueMenuOrder(saved, response);
        return response;
    }

    @Transactional
    public MenuOrderDtos.OrderResponse submit(Long menuId, String tableSessionToken) {
        return submit(menuId, tableSessionToken, null);
    }

    /**
     * Şubenin akışında garson onayı varsa QR siparişi "Onay bekliyor" olarak kalır; hesaba, stoğa ve mutfak
     * yazıcısına garson onayladığında ({@code MenuWaiterOrderService#confirm}) düşer.
     */
    private MenuOrderDtos.OrderResponse submitForApproval(MenuOrder order, UUID analyticsSessionId) {
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(MenuOrderStatus.SUBMITTED);
        order.setOrderSource(OrderSource.QR);
        order.setSubmittedAt(now);
        order.setUpdatedAt(now);
        if (analyticsSessionId != null) {
            order.setAnalyticsSessionId(analyticsSessionId);
        }
        securityUtils.findCurrentCustomerId().ifPresent(order::setCustomerId);
        MenuOrder saved = menuOrderRepository.save(order);
        orderAuditService.record(saved, OrderAuditAction.CREATED, null, "{\"source\":\"QR\",\"approval\":true}");
        return toOrderResponse(saved);
    }

    /** Onay bekleyen bir sipariş onaylandığında mutfak fişini basar. */
    public void enqueuePrint(MenuOrder order) {
        printOrderEnqueueService.enqueueMenuOrder(order, toOrderResponse(order));
    }

    @Transactional(readOnly = true)
    public MenuOrderDtos.OrderResponse getOrder(Long menuId, String tableSessionToken, Long orderId) {
        TableSession session = requireSessionForMenu(menuId, tableSessionToken);
        MenuOrder order = menuOrderRepository.findByIdAndTableSessionId(orderId, session.getId())
                .orElseThrow(() -> new NotFoundException("Sipariş bulunamadı"));
        return toOrderResponse(order);
    }

    @Transactional(readOnly = true)
    public List<MenuOrderDtos.OrderResponse> merchantList(Long menuId, String status) {
        return merchantList(menuId, status, null);
    }

    /**
     * @param days when set, "ALL" lists only orders submitted in the last [days] days (today counts as one);
     *             without it a busy menu's whole history comes back, which mobile clients can't load.
     */
    @Transactional(readOnly = true)
    public List<MenuOrderDtos.OrderResponse> merchantList(Long menuId, String status, Integer days) {
        Menu menu = requireOwnedMenu(menuId);
        List<MenuOrderDtos.OrderResponse> listed;
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status.trim())) {
            List<MenuOrderStatus> listedStatuses = List.of(
                    MenuOrderStatus.CONFIRMED,
                    MenuOrderStatus.PREPARING,
                    MenuOrderStatus.READY,
                    MenuOrderStatus.SERVED,
                    MenuOrderStatus.PAID,
                    MenuOrderStatus.SUBMITTED,
                    MenuOrderStatus.CANCELLED
            );
            List<MenuOrder> orders = days == null || days <= 0
                    ? menuOrderRepository.findByMenuIdAndStatusInOrderBySubmittedAtDesc(menuId, listedStatuses)
                    : menuOrderRepository.findByMenuIdAndStatusInAndSubmittedAtGreaterThanEqualOrderBySubmittedAtDesc(
                            menuId,
                            listedStatuses,
                            LocalDate.now().minusDays(days - 1L).atStartOfDay()
                    );
            listed = new ArrayList<>(toOrderResponses(menuId, orders));
        } else {
            MenuOrderStatus orderStatus = parseStatus(status, MenuOrderStatus.CONFIRMED);
            listed = new ArrayList<>(toOrderResponses(
                    menuId,
                    menuOrderRepository.findByMenuIdAndStatusOrderBySubmittedAtDesc(menuId, orderStatus)
            ));
        }
        Predicate<Long> listsConnectionBranch = marketplaceOwnership(menuId, menu);
        listed.addAll(kitchenUberEatsService.listManagedForOwner(menu.getMerchantId(), listsConnectionBranch));
        listed.addAll(kitchenYemekSepetiService.listManagedForOwner(menu.getMerchantId(), listsConnectionBranch));
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status.trim())) {
            MenuOrderStatus orderStatus = parseStatus(status, MenuOrderStatus.CONFIRMED);
            return listed.stream().filter(order -> order.getStatus() == orderStatus).toList();
        }
        return listed;
    }

    /** True for the connection branches whose marketplace orders this menu lists; see {@link MarketplaceMenuOwner}. */
    private Predicate<Long> marketplaceOwnership(Long menuId, Menu menu) {
        List<MarketplaceMenuOwner.MenuRef> merchantMenus = menuRepository
                .findByMerchantIdAndChannelAndDeletedFalseOrderByMenuIdAsc(menu.getMerchantId(), menu.getChannel())
                .stream()
                .map(candidate -> new MarketplaceMenuOwner.MenuRef(
                        candidate.getMenuId(),
                        candidate.getBranchId(),
                        candidate.isActive()
                ))
                .toList();
        Map<Long, Long> ownerByBranch = new HashMap<>();
        return connectionBranchId -> menuId.equals(ownerByBranch.computeIfAbsent(
                connectionBranchId == null ? -1L : connectionBranchId,
                key -> MarketplaceMenuOwner.ownerMenuId(connectionBranchId, merchantMenus)
        ));
    }

    @Transactional(readOnly = true)
    public MenuOrderDtos.OrderResponse merchantGet(Long menuId, Long orderId) {
        requireOwnedMenu(menuId);
        return toOrderResponse(requireOrderForMenu(menuId, orderId));
    }

    @Transactional
    public MenuOrderDtos.OrderResponse merchantCancel(
            Long menuId,
            Long orderId,
            MenuOrderDtos.CancelOrderRequest request
    ) {
        requireOwnedMenu(menuId);
        MenuOrder order = requireOrderForMenu(menuId, orderId);
        if (!isCancellable(order.getStatus())) {
            throw new BadRequestException("Bu sipariş iptal edilemez");
        }
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(MenuOrderStatus.CANCELLED);
        order.setCancelledAt(now);
        order.setRejectedAt(now);
        if (request != null) {
            order.setCancelReason(request.getReason() != null ? request.getReason() : CancelReason.OTHER);
            order.setCancelReasonNote(trimToNull(request.getReasonNote()));
        } else {
            order.setCancelReason(CancelReason.OTHER);
        }
        order.setUpdatedAt(now);
        MenuOrder saved = menuOrderRepository.save(order);
        tableBillService.releaseCancelledOrderItems(saved);
        stockConsumptionService.reverseMenuOrder(saved);
        orderAuditService.record(saved, OrderAuditAction.CANCELLED, null, null);
        return toOrderResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<MenuOrderDtos.OrderResponse> merchantKitchenQueue(Long menuId) {
        Menu menu = requireOwnedMenu(menuId);
        List<MenuOrderDtos.OrderResponse> active = menuOrderRepository
                .findByMenuIdAndStatusInOrderBySubmittedAtDesc(
                        menuId,
                        List.of(
                                MenuOrderStatus.CONFIRMED,
                                MenuOrderStatus.PREPARING,
                                MenuOrderStatus.READY
                        )
                )
                .stream()
                .map(this::toOrderResponse)
                .toList();
        LocalDateTime start = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Istanbul")).atStartOfDay();
        LocalDateTime end = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Istanbul")).atTime(java.time.LocalTime.MAX);
        List<MenuOrderDtos.OrderResponse> servedToday = menuOrderRepository
                .findByMenuIdAndStatusInAndSubmittedAtBetweenOrderBySubmittedAtDesc(
                        menuId,
                        List.of(MenuOrderStatus.SERVED),
                        start,
                        end
                )
                .stream()
                .map(this::toOrderResponse)
                .toList();
        List<MenuOrderDtos.OrderResponse> cancelledToday = menuOrderRepository
                .findByMenuIdAndStatusInAndSubmittedAtBetweenOrderBySubmittedAtDesc(
                        menuId,
                        List.of(MenuOrderStatus.CANCELLED),
                        start,
                        end
                )
                .stream()
                .filter(MenuOrderService::reachedKitchen)
                .map(this::toOrderResponse)
                .toList();
        List<MenuOrderDtos.OrderResponse> combined = new ArrayList<>(active);
        combined.addAll(tableBillService.hideClosedTableTickets(servedToday));
        combined.addAll(cancelledToday);
        combined.addAll(kitchenUberEatsService.listActiveForBranch(menu.getMerchantId(), menu.getBranchId()));
        combined.addAll(kitchenYemekSepetiService.listActiveForBranch(menu.getMerchantId(), menu.getBranchId()));
        return combined;
    }

    @Transactional
    public MenuOrderDtos.OrderResponse merchantMarkPreparing(Long menuId, Long orderId, String source) {
        if (KitchenUberEatsMapper.isUberEatsSource(source) || KitchenUberEatsMapper.isYemekSepetiSource(source)) {
            throw new BadRequestException("Pazar yeri siparişlerinde hazırlık adımı yok");
        }
        return merchantKitchenTransition(menuId, orderId, MenuOrderStatus.CONFIRMED, MenuOrderStatus.PREPARING);
    }

    @Transactional
    public MenuOrderDtos.OrderResponse merchantMarkPreparing(Long menuId, Long orderId) {
        return merchantMarkPreparing(menuId, orderId, null);
    }

    @Transactional
    public MenuOrderDtos.OrderResponse merchantMarkReady(Long menuId, Long orderId, String source) {
        if (KitchenUberEatsMapper.isUberEatsSource(source)) {
            Menu menu = requireOwnedMenu(menuId);
            return kitchenUberEatsService.markReady(menu.getMerchantId(), orderId);
        }
        if (KitchenUberEatsMapper.isYemekSepetiSource(source)) {
            Menu menu = requireOwnedMenu(menuId);
            return kitchenYemekSepetiService.markReady(menu.getMerchantId(), orderId);
        }
        return merchantKitchenTransition(menuId, orderId, MenuOrderStatus.PREPARING, MenuOrderStatus.READY);
    }

    @Transactional
    public MenuOrderDtos.OrderResponse merchantMarkReady(Long menuId, Long orderId) {
        return merchantMarkReady(menuId, orderId, null);
    }

    @Transactional
    public MenuOrderDtos.OrderResponse merchantMarkServed(Long menuId, Long orderId) {
        return merchantKitchenTransition(menuId, orderId, MenuOrderStatus.READY, MenuOrderStatus.SERVED);
    }

    @Transactional
    public MenuOrderDtos.OrderResponse merchantUpdateKitchenNote(Long menuId, Long orderId, String note) {
        requireOwnedMenu(menuId);
        MenuOrder order = requireOrderForMenu(menuId, orderId);
        order.setKitchenNote(trimToNull(note));
        order.setUpdatedAt(LocalDateTime.now());
        return toOrderResponse(menuOrderRepository.save(order));
    }

    public MenuOrder applyKitchenTransition(
            MenuOrder order,
            MenuOrderStatus expected,
            MenuOrderStatus next,
            Long actorWaiterId
    ) {
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
            if (actorWaiterId != null) {
                order.setStaffId(actorWaiterId);
            }
        }
        order.setUpdatedAt(now);
        MenuOrder saved = menuOrderRepository.save(order);
        orderAuditService.record(
                saved,
                OrderAuditAction.STATUS_CHANGED,
                actorWaiterId,
                "{\"from\":\"" + expected + "\",\"to\":\"" + next + "\"}"
        );
        return saved;
    }

    @Transactional
    public MenuOrderDtos.OrderResponse merchantRevertKitchen(
            Long menuId,
            Long orderId,
            MenuOrderStatus target,
            String source
    ) {
        requireKitchenRevertSource(source);
        requireOwnedMenu(menuId);
        MenuOrder order = requireOrderForMenu(menuId, orderId);
        return toOrderResponse(applyKitchenRevert(order, target, null));
    }

    public static void requireKitchenRevertSource(String source) {
        if (KitchenUberEatsMapper.isUberEatsSource(source) || KitchenUberEatsMapper.isYemekSepetiSource(source)) {
            throw new BadRequestException("Pazar yeri siparişleri geri alınamaz");
        }
    }

    public static void requireKitchenCancelSource(String source) {
        if (KitchenUberEatsMapper.isUberEatsSource(source) || KitchenUberEatsMapper.isYemekSepetiSource(source)) {
            throw new BadRequestException("Pazar yeri siparişleri platform üzerinden iptal edilir");
        }
    }

    /** Cancelled tickets that were confirmed first; waiter rejections never hit the kitchen board. */
    public static boolean reachedKitchen(MenuOrder order) {
        return order.getConfirmedAt() != null;
    }

    /** Moves a kitchen ticket back one or more steps (READY → PREPARING/CONFIRMED, PREPARING → CONFIRMED). */
    public MenuOrder applyKitchenRevert(MenuOrder order, MenuOrderStatus target, Long actorWaiterId) {
        MenuOrderStatus current = order.getStatus();
        boolean allowed = (current == MenuOrderStatus.PREPARING && target == MenuOrderStatus.CONFIRMED)
                || (current == MenuOrderStatus.READY
                    && (target == MenuOrderStatus.PREPARING || target == MenuOrderStatus.CONFIRMED));
        if (!allowed) {
            throw new BadRequestException("Sipariş " + current + " durumundan " + target + " durumuna geri alınamaz");
        }
        order.setStatus(target);
        order.setReadyAt(null);
        if (target == MenuOrderStatus.CONFIRMED) {
            order.setPreparedAt(null);
        }
        order.setUpdatedAt(LocalDateTime.now());
        MenuOrder saved = menuOrderRepository.save(order);
        orderAuditService.record(
                saved,
                OrderAuditAction.STATUS_CHANGED,
                actorWaiterId,
                "{\"from\":\"" + current + "\",\"to\":\"" + target + "\"}"
        );
        return saved;
    }

    private MenuOrderDtos.OrderResponse merchantKitchenTransition(
            Long menuId,
            Long orderId,
            MenuOrderStatus expected,
            MenuOrderStatus next
    ) {
        requireOwnedMenu(menuId);
        MenuOrder order = requireOrderForMenu(menuId, orderId);
        return toOrderResponse(applyKitchenTransition(order, expected, next, null));
    }

    public static boolean isCancellable(MenuOrderStatus status) {
        return status == MenuOrderStatus.CONFIRMED
                || status == MenuOrderStatus.SUBMITTED
                || status == MenuOrderStatus.PREPARING
                || status == MenuOrderStatus.READY;
    }

    public static boolean isActiveKitchen(MenuOrderStatus status) {
        return status == MenuOrderStatus.CONFIRMED
                || status == MenuOrderStatus.PREPARING
                || status == MenuOrderStatus.READY
                || status == MenuOrderStatus.SERVED;
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
            Map<Long, List<MenuProductOptionGroup>> groupsByProduct =
                    menuProductOptionService.loadEntitiesByProductIds(productsById.keySet());
            Map<String, MenuOrderItem> mergedByLineKey = new LinkedHashMap<>();

            for (MenuOrderDtos.CartItemRequest itemRequest : items) {
                MenuProduct product = productsById.get(itemRequest.getProductId());
                if (product == null) {
                    throw new BadRequestException("Ürün bulunamadı: " + itemRequest.getProductId());
                }
                if (!product.isAvailable()) {
                    throw new BadRequestException("Ürün şu an siparişe kapalı: " + product.getName());
                }

                MenuProductOptionService.ResolvedSelections resolved = menuProductOptionService.resolveSelections(
                        product.getProductId(),
                        groupsByProduct.getOrDefault(product.getProductId(), List.of()),
                        itemRequest.getSelectedOptionIds()
                );

                BigDecimal basePrice = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
                BigDecimal unitPrice = basePrice.add(resolved.priceDeltaTotal());
                int quantity = itemRequest.getQuantity();
                String note = trimToNull(itemRequest.getNote());
                String key = MenuProductOptionService.lineKey(product.getProductId(), resolved.selectedOptions());

                MenuOrderItem existing = mergedByLineKey.get(key);
                if (existing != null) {
                    int nextQty = existing.getQuantity() + quantity;
                    existing.setQuantity(nextQty);
                    existing.setLineTotal(existing.getUnitPrice().multiply(BigDecimal.valueOf(nextQty)));
                    if (existing.getNote() == null && note != null) {
                        existing.setNote(note);
                    }
                    continue;
                }

                BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
                MenuOrderItem item = MenuOrderItem.builder()
                        .productId(product.getProductId())
                        .productName(product.getName())
                        .unitPrice(unitPrice)
                        .quantity(quantity)
                        .note(note)
                        .selectedOptions(new ArrayList<>(resolved.selectedOptions()))
                        .lineTotal(lineTotal)
                        .build();
                mergedByLineKey.put(key, item);

                if (product.getCurrency() != null && !product.getCurrency().isBlank()) {
                    currency = product.getCurrency();
                }
            }

            for (MenuOrderItem item : mergedByLineKey.values()) {
                total = total.add(item.getLineTotal());
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

    private TableSession requireSessionForMenu(Long menuId, String tableSessionToken) {
        TableSession session = tableSessionService.requireActiveSession(tableSessionToken);
        if (!session.getMenuId().equals(menuId)) {
            throw new BadRequestException("Masa oturumu bu menüye ait değil");
        }
        return session;
    }

    private Menu requireOwnedMenu(Long menuId) {
        Menu menu = menuRepository.findById(menuId)
                .filter(m -> !m.isDeleted())
                .orElseThrow(() -> new NotFoundException("Menü bulunamadı"));
        Long currentUserId = securityUtils.getCurrentMerchantId();
        if (!currentUserId.equals(menu.getMerchantId())) {
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
        String tableName = order.getTableId() == null
                ? "Masa yok"
                : restaurantTableRepository.findById(order.getTableId())
                        .map(RestaurantTable::getName)
                        .orElse(null);
        String waiterName = order.getStaffId() == null
                ? null
                : merchantStaffRepository.findById(order.getStaffId())
                        .map(MerchantStaff::getDisplayName)
                        .orElse(null);
        Customer customer = order.getCustomerId() == null
                ? null
                : customerRepository.findById(order.getCustomerId()).orElse(null);
        return buildOrderResponse(order, tableName, waiterName, customer, campaignEvaluationService.summarizeOrder(order));
    }

    /** Converts a whole list with one lookup per kind of related row instead of several per order. */
    private List<MenuOrderDtos.OrderResponse> toOrderResponses(Long menuId, List<MenuOrder> orders) {
        Map<Long, String> tableNames = new HashMap<>();
        restaurantTableRepository.findAllById(idsOf(orders, MenuOrder::getTableId))
                .forEach(table -> tableNames.put(table.getId(), table.getName()));
        Map<Long, String> waiterNames = new HashMap<>();
        merchantStaffRepository.findAllById(idsOf(orders, MenuOrder::getStaffId))
                .forEach(staff -> waiterNames.put(staff.getId(), staff.getDisplayName()));
        Map<Long, Customer> customers = new HashMap<>();
        customerRepository.findAllById(idsOf(orders, MenuOrder::getCustomerId))
                .forEach(customer -> customers.put(customer.getId(), customer));
        Map<Long, CampaignDtos.OrderCampaignSummary> summaries = campaignEvaluationService.summarizeOrders(menuId, orders);
        return orders.stream()
                .map(order -> buildOrderResponse(
                        order,
                        order.getTableId() == null ? "Masa yok" : tableNames.get(order.getTableId()),
                        waiterNames.get(order.getStaffId()),
                        customers.get(order.getCustomerId()),
                        summaries.get(order.getId())
                ))
                .toList();
    }

    private static Set<Long> idsOf(List<MenuOrder> orders, Function<MenuOrder, Long> id) {
        return orders.stream().map(id).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private MenuOrderDtos.OrderResponse buildOrderResponse(
            MenuOrder order,
            String tableName,
            String waiterName,
            Customer customer,
            CampaignDtos.OrderCampaignSummary campaignSummary
    ) {
        String customerName = null;
        String customerEmail = null;
        if (customer != null) {
            String displayName = customer.getDisplayName();
            customerName = displayName == null || displayName.isBlank() ? null : displayName;
            customerEmail = customer.getEmail();
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
                        .selectedOptions(toSelectedOptionResponses(item.getSelectedOptions()))
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
                .orderSource(order.getOrderSource())
                .totalAmount(order.getTotalAmount())
                .currency(order.getCurrency())
                .note(order.getNote())
                .staffId(order.getStaffId())
                .createdByStaffId(order.getCreatedByStaffId())
                .cancelledByStaffId(order.getCancelledByStaffId())
                .waiterName(waiterName)
                .waiterNote(order.getWaiterNote())
                .kitchenNote(order.getKitchenNote())
                .billId(order.getBillId())
                .commissionAmount(order.getCommissionAmount())
                .analyticsSessionId(order.getAnalyticsSessionId())
                .items(items)
                .submittedAt(order.getSubmittedAt())
                .confirmedAt(order.getConfirmedAt())
                .preparedAt(order.getPreparedAt())
                .readyAt(order.getReadyAt())
                .servedAt(order.getServedAt())
                .rejectedAt(order.getRejectedAt())
                .cancelledAt(order.getCancelledAt())
                .cancelReason(order.getCancelReason())
                .cancelReasonNote(order.getCancelReasonNote())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .campaignSummary(campaignSummary)
                .build();
    }

    private static List<MenuOrderDtos.SelectedOptionResponse> toSelectedOptionResponses(
            List<SelectedMenuOption> selectedOptions
    ) {
        if (selectedOptions == null || selectedOptions.isEmpty()) {
            return List.of();
        }
        return selectedOptions.stream()
                .map(option -> MenuOrderDtos.SelectedOptionResponse.builder()
                        .groupId(option.getGroupId())
                        .groupName(option.getGroupName())
                        .optionId(option.getOptionId())
                        .optionName(option.getOptionName())
                        .priceDelta(option.getPriceDelta())
                        .build())
                .toList();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
