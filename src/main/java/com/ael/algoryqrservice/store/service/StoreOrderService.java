package com.ael.algoryqrservice.store.service;

import com.ael.algoryqrservice.store.model.Merchant;
import com.ael.algoryqrservice.store.model.StoreCourier;
import com.ael.algoryqrservice.store.model.StoreDeliveryType;
import com.ael.algoryqrservice.store.model.StoreOrder;
import com.ael.algoryqrservice.store.model.StoreOrderActorType;
import com.ael.algoryqrservice.store.model.StoreOrderStatus;
import com.ael.algoryqrservice.store.model.StoreOrderStatusHistory;
import com.ael.algoryqrservice.store.model.StorePaymentStatus;
import com.ael.algoryqrservice.store.model.dto.StorePublicDtos;
import com.ael.algoryqrservice.store.repository.MerchantRepository;
import com.ael.algoryqrservice.store.repository.StoreCourierRepository;
import com.ael.algoryqrservice.store.repository.StoreOrderRepository;
import com.ael.algoryqrservice.store.repository.StoreOrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StoreOrderService {

    private final StoreOrderRepository storeOrderRepository;
    private final StoreOrderStatusHistoryRepository statusHistoryRepository;
    private final StoreCourierRepository storeCourierRepository;
    private final MerchantRepository merchantRepository;
    private final StoreOrderPricing storeOrderPricing;
    private final StoreOrderStatusMachine statusMachine;
    private final StoreOpeningHours storeOpeningHours;
    private final StoreTokenGenerator storeTokenGenerator;

    @Transactional
    public StoreOrder placeOrder(Merchant merchant, StorePublicDtos.CreateOrderRequest request, Long customerId) {
        requireAcceptingOrders(merchant, request);
        StoreOrderPricing.PricedOrder priced = storeOrderPricing.price(merchant, request);

        StoreOrder order = StoreOrder.builder()
                .merchantId(merchant.getId())
                .orderNo(nextOrderNo(merchant.getId()))
                .publicToken(storeTokenGenerator.generateUnique(storeOrderRepository::existsByPublicToken))
                .customerId(customerId)
                .customerName(request.customerName().trim())
                .customerPhone(request.customerPhone().trim())
                .deliveryType(request.deliveryType())
                .paymentMethod(request.paymentMethod())
                .status(StoreOrderStatus.PENDING)
                .subtotal(priced.subtotal())
                .deliveryFee(priced.deliveryFee())
                .totalAmount(priced.total())
                .currency(merchant.getCurrency())
                .note(request.note())
                .build();
        applyAddress(order, request);
        priced.items().forEach(order::addItem);

        StoreOrder saved = storeOrderRepository.save(order);
        recordTransition(saved, null, StoreOrderStatus.PENDING, StoreOrderActorType.CUSTOMER, customerId, null);
        return saved;
    }

    @Transactional
    public StoreOrder advance(
            Long merchantId,
            Long orderId,
            StoreOrderStatus target,
            Long actorUserId,
            String reason
    ) {
        StoreOrder order = requireOrder(merchantId, orderId);
        StoreOrderStatus previous = order.getStatus();
        statusMachine.requireTransition(previous, target);
        order.setStatus(target);
        stampTransition(order, target, reason);
        StoreOrder saved = storeOrderRepository.save(order);
        recordTransition(saved, previous, target, StoreOrderActorType.MERCHANT, actorUserId, reason);
        return saved;
    }

    @Transactional
    public StoreOrder assignCourier(Long merchantId, Long orderId, Long courierId, Long actorUserId) {
        StoreOrder order = requireOrder(merchantId, orderId);
        if (order.getDeliveryType() != StoreDeliveryType.DELIVERY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Gel-al siparişine kurye atanamaz");
        }
        if (statusMachine.isTerminal(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tamamlanmış siparişe kurye atanamaz");
        }
        StoreCourier courier = storeCourierRepository.findByIdAndMerchantIdAndDeletedFalse(courierId, merchantId)
                .filter(StoreCourier::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kurye bulunamadı"));
        order.setCourierId(courier.getId());
        order.setAssignedAt(LocalDateTime.now());
        return storeOrderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public StoreOrder requireOrder(Long merchantId, Long orderId) {
        return storeOrderRepository.findByIdAndMerchantId(orderId, merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sipariş bulunamadı"));
    }

    @Transactional(readOnly = true)
    public StoreOrder requireByPublicToken(String publicToken) {
        return storeOrderRepository.findByPublicToken(publicToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sipariş bulunamadı"));
    }

    @Transactional(readOnly = true)
    public Optional<StoreCourier> findCourier(Long courierId) {
        return courierId == null ? Optional.empty() : storeCourierRepository.findById(courierId);
    }

    private void requireAcceptingOrders(Merchant merchant, StorePublicDtos.CreateOrderRequest request) {
        if (!storeOpeningHours.isOpenAt(merchant, LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mağaza şu anda kapalı");
        }
        if (!merchant.getDeliveryTypes().contains(request.deliveryType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu teslimat türü desteklenmiyor");
        }
        if (!merchant.getPaymentMethods().contains(request.paymentMethod())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu ödeme yöntemi desteklenmiyor");
        }
        if (request.deliveryType() == StoreDeliveryType.DELIVERY && !hasAddress(request)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Teslimat adresi zorunludur");
        }
    }

    private boolean hasAddress(StorePublicDtos.CreateOrderRequest request) {
        return request.address() != null
                && request.address().addressText() != null
                && !request.address().addressText().isBlank();
    }

    private void applyAddress(StoreOrder order, StorePublicDtos.CreateOrderRequest request) {
        StorePublicDtos.DeliveryAddressRequest address = request.address();
        if (address == null) {
            return;
        }
        order.setAddressText(address.addressText());
        order.setCity(address.city());
        order.setDistrict(address.district());
        order.setBuildingNo(address.buildingNo());
        order.setFloorNo(address.floorNo());
        order.setDoorNo(address.doorNo());
        order.setDirections(address.directions());
        order.setLatitude(address.latitude());
        order.setLongitude(address.longitude());
    }

    private void stampTransition(StoreOrder order, StoreOrderStatus target, String reason) {
        LocalDateTime now = LocalDateTime.now();
        switch (target) {
            case CONFIRMED -> order.setConfirmedAt(now);
            case PREPARING -> order.setPreparingAt(now);
            case READY -> order.setReadyAt(now);
            case ON_THE_WAY -> order.setDispatchedAt(now);
            case DELIVERED -> {
                order.setDeliveredAt(now);
                order.setPaymentStatus(StorePaymentStatus.PAID);
            }
            case REJECTED -> {
                order.setRejectedAt(now);
                order.setRejectReason(reason);
            }
            case CANCELLED -> {
                order.setCancelledAt(now);
                order.setCancelReason(reason);
            }
            case PENDING -> {
            }
        }
    }

    private void recordTransition(
            StoreOrder order,
            StoreOrderStatus from,
            StoreOrderStatus to,
            StoreOrderActorType actorType,
            Long actorId,
            String note
    ) {
        statusHistoryRepository.save(StoreOrderStatusHistory.builder()
                .orderId(order.getId())
                .fromStatus(from)
                .toStatus(to)
                .changedByType(actorType)
                .changedById(actorId)
                .note(note)
                .build());
    }

    private String nextOrderNo(Long merchantId) {
        Merchant locked = merchantRepository.lockById(merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mağaza bulunamadı"));
        long next = locked.getOrderCounter() + 1;
        locked.setOrderCounter(next);
        merchantRepository.save(locked);
        return String.format("%06d", next);
    }
}
