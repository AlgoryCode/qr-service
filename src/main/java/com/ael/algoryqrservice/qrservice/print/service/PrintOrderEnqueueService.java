package com.ael.algoryqrservice.qrservice.print.service;

import com.ael.algoryqrservice.qrservice.integration.ubereats.mapper.UberEatsPayloadMapper;
import com.ael.algoryqrservice.qrservice.integration.ubereats.model.UberEatsOrder;
import com.ael.algoryqrservice.qrservice.integration.ubereats.model.dto.UberEatsDtos;
import com.ael.algoryqrservice.qrservice.integration.yemeksepeti.mapper.YemekSepetiPayloadMapper;
import com.ael.algoryqrservice.qrservice.integration.yemeksepeti.model.YemekSepetiOrder;
import com.ael.algoryqrservice.qrservice.integration.yemeksepeti.model.dto.YemekSepetiDtos;
import com.ael.algoryqrservice.qrservice.model.Branch;
import com.ael.algoryqrservice.qrservice.model.Menu;
import com.ael.algoryqrservice.qrservice.model.MenuOrder;
import com.ael.algoryqrservice.qrservice.order.dto.MenuOrderDtos;
import com.ael.algoryqrservice.qrservice.print.model.PrintSourceType;
import com.ael.algoryqrservice.qrservice.repository.BranchRepository;
import com.ael.algoryqrservice.qrservice.repository.MenuRepository;
import com.ael.algoryqrservice.qrservice.order.service.KitchenUberEatsMapper;
import com.ael.algoryqrservice.qrservice.store.model.Merchant;
import com.ael.algoryqrservice.qrservice.store.model.StoreDeliveryType;
import com.ael.algoryqrservice.qrservice.store.model.StoreOrder;
import com.ael.algoryqrservice.qrservice.store.model.StoreOrderItem;
import com.ael.algoryqrservice.qrservice.store.model.StorePaymentMethod;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class PrintOrderEnqueueService {

    private final PrintJobService printJobService;
    private final MenuRepository menuRepository;
    private final BranchRepository branchRepository;
    private final UberEatsPayloadMapper uberEatsPayloadMapper;
    private final YemekSepetiPayloadMapper yemekSepetiPayloadMapper;
    private final ObjectMapper objectMapper;

    public void enqueueMenuOrder(MenuOrder order, MenuOrderDtos.OrderResponse response) {
        if (order == null || order.getId() == null) {
            return;
        }
        Menu menu = menuRepository.findById(order.getMenuId()).orElse(null);
        if (menu == null) {
            return;
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("ticketKind", "BILGI_FISI");
        payload.put("channelLabel", channelLabel(order.getOrderSource() == null
                ? null
                : order.getOrderSource().name()));
        payload.put("id", order.getId());
        payload.put("orderNo", String.valueOf(order.getId()));
        if (menu.getBranchId() != null) {
            payload.put("branchId", menu.getBranchId());
        }
        if (response != null) {
            putText(payload, "tableName", response.getTableName());
            putText(payload, "customerName", response.getCustomerName());
            putText(payload, "waiterName", response.getWaiterName());
            putText(payload, "note", response.getNote());
            putText(payload, "waiterNote", response.getWaiterNote());
            putText(payload, "kitchenNote", response.getKitchenNote());
            putText(payload, "currency", response.getCurrency() == null ? "TRY" : response.getCurrency());
            putAmount(payload, "totalAmount", response.getTotalAmount());
            if (response.getSubmittedAt() != null) {
                payload.put("submittedAt", response.getSubmittedAt().toString());
            } else if (response.getCreatedAt() != null) {
                payload.put("submittedAt", response.getCreatedAt().toString());
            }
            ArrayNode items = payload.putArray("items");
            if (response.getItems() != null) {
                for (MenuOrderDtos.OrderItemResponse item : response.getItems()) {
                    if (item == null) {
                        continue;
                    }
                    ObjectNode line = items.addObject();
                    line.put("productName", item.getProductName() == null ? "Urun" : item.getProductName());
                    line.put("quantity", item.getQuantity());
                    putAmount(line, "unitPrice", item.getUnitPrice());
                    putAmount(line, "lineTotal", item.getLineTotal());
                    putText(line, "note", item.getNote());
                    ArrayNode options = line.putArray("selectedOptions");
                    if (item.getSelectedOptions() != null) {
                        for (MenuOrderDtos.SelectedOptionResponse option : item.getSelectedOptions()) {
                            if (option == null || option.getOptionName() == null || option.getOptionName().isBlank()) {
                                continue;
                            }
                            ObjectNode opt = options.addObject();
                            opt.put("optionName", option.getOptionName());
                            putAmount(opt, "priceDelta", option.getPriceDelta());
                        }
                    }
                }
            }
        }
        attachBranchHeader(payload, menu.getBranchId());
        printJobService.enqueueKitchenTicket(
                menu.getMerchantId(),
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
        payload.put("ticketKind", "BILGI_FISI");
        if (branchId != null) {
            payload.put("branchId", branchId);
        }
        putText(payload, "deliveryAddress", order.getDeliveryAddress());
        putText(payload, "customerPhone", order.getCustomerPhone());
        JsonNode raw = readRaw(order.getRawPayload());
        String orderNo = raw == null ? null : uberEatsPayloadMapper.orderNumber(raw);
        putText(payload, "orderNo", firstNonBlank(orderNo, order.getExternalOrderId()));
        if (raw != null) {
            putText(payload, "deliveryType", uberEatsPayloadMapper.deliveryType(raw));
            putText(payload, "paymentMethod", uberEatsPayloadMapper.paymentMethod(raw));
        }
        attachBranchHeader(payload, branchId);
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
                com.ael.algoryqrservice.qrservice.model.enums.OrderSource.YEMEKSEPETI,
                "Yemeksepeti"
        );
        ObjectNode payload = objectMapper.valueToTree(kitchen);
        payload.put("channelLabel", "Yemeksepeti");
        payload.put("ticketKind", "BILGI_FISI");
        if (branchId != null) {
            payload.put("branchId", branchId);
        }
        putText(payload, "deliveryAddress", order.getDeliveryAddress());
        putText(payload, "customerPhone", order.getCustomerPhone());
        JsonNode raw = readRaw(order.getRawPayload());
        String orderNo = raw == null ? null : yemekSepetiPayloadMapper.orderNumber(raw);
        putText(payload, "orderNo", firstNonBlank(orderNo, order.getExternalOrderId()));
        if (raw != null) {
            putText(payload, "deliveryType", yemekSepetiPayloadMapper.deliveryType(raw));
            putText(payload, "paymentMethod", yemekSepetiPayloadMapper.paymentMethod(raw));
        }
        attachBranchHeader(payload, branchId);
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
        payload.put("ticketKind", "BILGI_FISI");
        payload.put("customerName", order.getCustomerName());
        payload.put("customerPhone", order.getCustomerPhone());
        payload.put("note", order.getNote());
        payload.put("totalAmount", nullSafe(order.getTotalAmount()));
        payload.put("currency", order.getCurrency());
        putText(payload, "paymentMethod", paymentLabel(order.getPaymentMethod()));
        putText(payload, "deliveryType", deliveryLabel(order.getDeliveryType()));
        putAmount(payload, "subtotal", order.getSubtotal());
        putAmount(payload, "deliveryFee", order.getDeliveryFee());
        putAmount(payload, "discountAmount", order.getDiscountAmount());
        if (merchant.getBranchId() != null) {
            payload.put("branchId", merchant.getBranchId());
        }
        putText(payload, "deliveryAddress", storeDeliveryAddress(order));
        attachStoreBusiness(payload, merchant);
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
                merchant.getOwnerMerchantId(),
                merchant.getBranchId(),
                PrintSourceType.STORE_ORDER,
                String.valueOf(order.getId()),
                payload
        );
    }

    private void attachBranchHeader(ObjectNode payload, Long branchId) {
        if (branchId == null) {
            return;
        }
        branchRepository.findById(branchId).ifPresent(branch -> attachBranch(payload, branch));
    }

    private static void attachBranch(ObjectNode payload, Branch branch) {
        putText(payload, "businessName", branch.getName());
        putText(payload, "businessAddress", branch.getAddress());
        putText(payload, "businessPhone", branch.getPhone());
    }

    private static void attachStoreBusiness(ObjectNode payload, Merchant merchant) {
        putText(payload, "businessName", merchant.getBusinessName());
        putText(payload, "legalName", merchant.getLegalName());
        putText(payload, "taxOffice", merchant.getTaxOffice());
        putText(payload, "taxNumber", merchant.getTaxNumber());
        putText(payload, "businessPhone", merchant.getPhone());
        putText(payload, "businessAddress", joinParts(merchant.getAddress(), merchant.getDistrict(), merchant.getCity()));
    }

    private static String storeDeliveryAddress(StoreOrder order) {
        return joinParts(
                order.getAddressText(),
                order.getDistrict(),
                order.getCity(),
                labeled("Bina", order.getBuildingNo()),
                labeled("Kat", order.getFloorNo()),
                labeled("Kapı", order.getDoorNo()),
                order.getDirections()
        );
    }

    private JsonNode readRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String paymentLabel(StorePaymentMethod method) {
        if (method == null) {
            return null;
        }
        return switch (method) {
            case CASH_ON_DELIVERY -> "Nakit";
            case CARD_ON_DELIVERY -> "Kredi kartı";
        };
    }

    private static String deliveryLabel(StoreDeliveryType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case DELIVERY -> "Teslimat";
            case PICKUP -> "Gel-al";
        };
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

    private static void putAmount(ObjectNode node, String field, BigDecimal value) {
        if (value == null) {
            return;
        }
        node.put(field, value.doubleValue());
    }

    private static void putText(ObjectNode node, String field, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        node.put(field, value.trim());
    }

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return null;
    }

    private static String labeled(String label, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return label + " " + value.trim();
    }

    private static String joinParts(String... parts) {
        String joined = Stream.of(parts)
                .filter(part -> part != null && !part.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(", "));
        return joined.isBlank() ? null : joined;
    }

    private static double nullSafe(BigDecimal value) {
        return value == null ? 0D : value.doubleValue();
    }
}
