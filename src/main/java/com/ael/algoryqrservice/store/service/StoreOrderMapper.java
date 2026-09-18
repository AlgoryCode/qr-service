package com.ael.algoryqrservice.store.service;

import com.ael.algoryqrservice.store.model.StoreCourier;
import com.ael.algoryqrservice.store.model.StoreOrder;
import com.ael.algoryqrservice.store.model.StoreOrderItem;
import com.ael.algoryqrservice.store.model.StoreOrderStatusHistory;
import com.ael.algoryqrservice.store.model.dto.StoreOrderDtos;
import com.ael.algoryqrservice.store.model.dto.StorePublicDtos;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StoreOrderMapper {

    public StoreOrderDtos.OrderSummary toSummary(StoreOrder order, StoreCourier courier) {
        return StoreOrderDtos.OrderSummary.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .status(order.getStatus())
                .deliveryType(order.getDeliveryType())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .customerName(order.getCustomerName())
                .customerPhone(order.getCustomerPhone())
                .totalAmount(order.getTotalAmount())
                .currency(order.getCurrency())
                .itemCount(order.getItems().size())
                .courier(toCourierBrief(courier))
                .createdAt(order.getCreatedAt())
                .build();
    }

    public StoreOrderDtos.OrderDetail toDetail(
            StoreOrder order,
            StoreCourier courier,
            List<StoreOrderStatusHistory> history
    ) {
        return StoreOrderDtos.OrderDetail.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .publicToken(order.getPublicToken())
                .status(order.getStatus())
                .deliveryType(order.getDeliveryType())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .customerId(order.getCustomerId())
                .customerName(order.getCustomerName())
                .customerPhone(order.getCustomerPhone())
                .address(toAddress(order))
                .note(order.getNote())
                .subtotal(order.getSubtotal())
                .deliveryFee(order.getDeliveryFee())
                .discountAmount(order.getDiscountAmount())
                .totalAmount(order.getTotalAmount())
                .currency(order.getCurrency())
                .courier(toCourierBrief(courier))
                .items(toItems(order.getItems()))
                .history(toHistory(history))
                .rejectReason(order.getRejectReason())
                .cancelReason(order.getCancelReason())
                .createdAt(order.getCreatedAt())
                .confirmedAt(order.getConfirmedAt())
                .readyAt(order.getReadyAt())
                .dispatchedAt(order.getDispatchedAt())
                .deliveredAt(order.getDeliveredAt())
                .build();
    }

    private StoreOrderDtos.Address toAddress(StoreOrder order) {
        return StoreOrderDtos.Address.builder()
                .addressText(order.getAddressText())
                .city(order.getCity())
                .district(order.getDistrict())
                .buildingNo(order.getBuildingNo())
                .floorNo(order.getFloorNo())
                .doorNo(order.getDoorNo())
                .directions(order.getDirections())
                .latitude(order.getLatitude())
                .longitude(order.getLongitude())
                .build();
    }

    private StoreOrderDtos.CourierBrief toCourierBrief(StoreCourier courier) {
        if (courier == null) {
            return null;
        }
        return StoreOrderDtos.CourierBrief.builder()
                .id(courier.getId())
                .fullName(courier.getFullName())
                .phone(courier.getPhone())
                .build();
    }

    private List<StorePublicDtos.OrderItemResponse> toItems(List<StoreOrderItem> items) {
        return items.stream()
                .map(item -> StorePublicDtos.OrderItemResponse.builder()
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .unitPrice(item.getUnitPrice())
                        .quantity(item.getQuantity())
                        .note(item.getNote())
                        .options(item.getSelectedOptions())
                        .lineTotal(item.getLineTotal())
                        .build())
                .toList();
    }

    private List<StoreOrderDtos.StatusEvent> toHistory(List<StoreOrderStatusHistory> history) {
        return history.stream()
                .map(event -> StoreOrderDtos.StatusEvent.builder()
                        .fromStatus(event.getFromStatus())
                        .toStatus(event.getToStatus())
                        .changedByType(event.getChangedByType())
                        .note(event.getNote())
                        .createdAt(event.getCreatedAt())
                        .build())
                .toList();
    }
}
