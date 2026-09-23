package com.ael.algoryqrservice.print.service;

import com.ael.algoryqrservice.integration.ubereats.model.UberEatsOrder;
import com.ael.algoryqrservice.integration.ubereats.model.dto.UberEatsDtos;
import com.ael.algoryqrservice.integration.yemeksepeti.model.YemekSepetiOrder;
import com.ael.algoryqrservice.integration.yemeksepeti.model.dto.YemekSepetiDtos;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuOrder;
import com.ael.algoryqrservice.model.dto.MenuOrderDtos;
import com.ael.algoryqrservice.print.model.PrintSourceType;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.service.KitchenUberEatsMapper;
import com.ael.algoryqrservice.store.model.Merchant;
import com.ael.algoryqrservice.store.model.StoreOrder;
import com.ael.algoryqrservice.store.model.StoreOrderItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PrintOrderEnqueueService {

    private final PrintJobService printJobService;
    private final MenuRepository menuRepository;
    private final ObjectMapper objectMapper;

    public void enqueueMenuOrder(MenuOrder order, MenuOrderDtos.OrderResponse response) {
        if (order == null || order.getId() == null) {
            return;
        }
        Menu menu = menuRepository.findById(order.getMenuId()).orElse(null);
        if (menu == null) {
            return;
        }
        JsonNode payload = objectMapper.valueToTree(response);
        ((ObjectNode) payload).put("channelLabel", channelLabel(order.getOrderSource() == null
                ? null
                : order.getOrderSource().name()));
        if (menu.getBranchId() != null) {
            ((ObjectNode) payload).put("branchId", menu.getBranchId());
        }
        printJobService.enqueueKitchenTicket(
                menu.getUserId(),
                menu.getBranchId(),
                PrintSourceType.MENU_ORDER,
                String.valueOf(order.getId()),
                payload
        );
    }

    public void enqueueUberEatsOrder(
            Long merchantId,
            Long branchId,
            UberEatsOrder order,
            List<UberEatsDtos.OrderItemResponse> items
    ) {
        if (merchantId == null || order == null || order.getId() == null) {
            return;
        }
        MenuOrderDtos.OrderResponse kitchen = KitchenUberEatsMapper.toKitchenOrder(order, items);
        ObjectNode payload = objectMapper.valueToTree(kitchen);
        payload.put("channelLabel", "Uber Eats");
        if (branchId != null) {
            payload.put("branchId", branchId);
        }
        if (order.getDeliveryAddress() != null) {
            payload.put("deliveryAddress", order.getDeliveryAddress());
        }
        if (order.getCustomerPhone() != null) {
            payload.put("customerPhone", order.getCustomerPhone());
        }
        printJobService.enqueueKitchenTicket(
                merchantId,
                branchId,
                PrintSourceType.UBER_EATS,
                String.valueOf(order.getId()),
                payload
        );
    }

    public void enqueueYemekSepetiOrder(
            Long merchantId,
            Long branchId,
            YemekSepetiOrder order,
            List<YemekSepetiDtos.OrderItemResponse> items
    ) {
        if (merchantId == null || order == null || order.getId() == null) {
            return;
        }
        List<MenuOrderDtos.OrderItemResponse> ticketItems = items == null ? List.of() : items.stream()
                .map(item -> KitchenUberEatsMapper.toTicketItem(
                        item.getProductId(),
                        item.getProductName(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getDetail() != null ? item.getDetail() : item.getOptions()
                ))
                .toList();
        MenuOrderDtos.OrderResponse kitchen = KitchenUberEatsMapper.toTicket(
                order.getId(),
                order.getPackageStatus(),
                order.getCustomerName(),
                order.getNote(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getPackageCreatedAt(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                ticketItems,
                com.ael.algoryqrservice.model.enums.OrderSource.YEMEKSEPETI,
                "Yemeksepeti"
        );
        ObjectNode payload = objectMapper.valueToTree(kitchen);
        payload.put("channelLabel", "Yemeksepeti");
        if (branchId != null) {
            payload.put("branchId", branchId);
        }
        if (order.getDeliveryAddress() != null) {
            payload.put("deliveryAddress", order.getDeliveryAddress());
        }
        if (order.getCustomerPhone() != null) {
            payload.put("customerPhone", order.getCustomerPhone());
        }
        printJobService.enqueueKitchenTicket(
                merchantId,
                branchId,
                PrintSourceType.YEMEKSEPETI,
                String.valueOf(order.getId()),
                payload
        );
    }

    public void enqueueStoreOrder(Merchant merchant, StoreOrder order) {
        if (merchant == null || order == null || order.getId() == null) {
            return;
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("id", order.getId());
        payload.put("orderNo", order.getOrderNo());
        payload.put("channelLabel", "Online Siparis");
        payload.put("tableName", order.getDeliveryType() == null ? "Online" : order.getDeliveryType().name());
        payload.put("customerName", order.getCustomerName());
        payload.put("customerPhone", order.getCustomerPhone());
        payload.put("note", order.getNote());
        payload.put("totalAmount", order.getTotalAmount() == null ? 0 : order.getTotalAmount().doubleValue());
        payload.put("currency", order.getCurrency());
        if (merchant.getBranchId() != null) {
            payload.put("branchId", merchant.getBranchId());
        }
        if (order.getAddressText() != null) {
            payload.put("deliveryAddress", order.getAddressText());
        }
        ArrayNode items = payload.putArray("items");
        if (order.getItems() != null) {
            for (StoreOrderItem item : order.getItems()) {
                ObjectNode line = items.addObject();
                line.put("productName", item.getProductName());
                line.put("quantity", item.getQuantity());
                line.put("unitPrice", nullSafe(item.getUnitPrice()));
                line.put("lineTotal", nullSafe(item.getLineTotal()));
                line.put("note", item.getNote());
            }
        }
        printJobService.enqueueKitchenTicket(
                merchant.getUserId(),
                merchant.getBranchId(),
                PrintSourceType.STORE_ORDER,
                String.valueOf(order.getId()),
                payload
        );
    }

    private static String channelLabel(String source) {
        if (source == null) {
            return "Siparis";
        }
        return switch (source) {
            case "QR" -> "QR Masa";
            case "WAITER" -> "Garson";
            case "UBER_EATS" -> "Uber Eats";
            case "YEMEKSEPETI" -> "Yemeksepeti";
            default -> source;
        };
    }

    private static double nullSafe(BigDecimal value) {
        return value == null ? 0D : value.doubleValue();
    }
}
