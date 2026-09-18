package com.ael.algoryqrservice.store.service;

import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.SelectedMenuOption;
import com.ael.algoryqrservice.model.dto.MenuDtos;
import com.ael.algoryqrservice.service.MenuProductOptionService;
import com.ael.algoryqrservice.store.model.Merchant;
import com.ael.algoryqrservice.store.model.StoreDeliveryType;
import com.ael.algoryqrservice.store.model.StoreOrderItem;
import com.ael.algoryqrservice.store.model.dto.StorePublicDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rebuilds every amount from stored catalog prices; nothing monetary is taken from the request body.
 */
@Component
@RequiredArgsConstructor
public class StoreOrderPricing {

    private final StoreCatalogService storeCatalogService;
    private final MenuProductOptionService menuProductOptionService;

    public record PricedOrder(List<StoreOrderItem> items, BigDecimal subtotal, BigDecimal deliveryFee, BigDecimal total) {
    }

    public PricedOrder price(Merchant merchant, StorePublicDtos.CreateOrderRequest request) {
        List<Long> requestedIds = request.items().stream().map(StorePublicDtos.OrderItemRequest::productId).toList();
        Map<Long, MenuProduct> products = storeCatalogService.loadAvailableProducts(merchant.getCatalogMenuId(), requestedIds);
        Map<Long, List<MenuDtos.MenuProductOptionGroupResponse>> optionGroups =
                menuProductOptionService.loadByProductIds(products.keySet());

        List<StoreOrderItem> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (StorePublicDtos.OrderItemRequest requested : request.items()) {
            MenuProduct product = products.get(requested.productId());
            if (product == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ürün satışta değil");
            }
            List<SelectedMenuOption> selected = resolveOptions(requested, optionGroups.get(product.getProductId()));
            BigDecimal unitPrice = basePrice(product).add(optionsTotal(selected));
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(requested.quantity()));
            items.add(StoreOrderItem.builder()
                    .productId(product.getProductId())
                    .productName(product.getName())
                    .unitPrice(unitPrice)
                    .quantity(requested.quantity())
                    .note(requested.note())
                    .selectedOptions(selected)
                    .lineTotal(lineTotal)
                    .build());
            subtotal = subtotal.add(lineTotal);
        }

        requireMinimumBasket(merchant, request.deliveryType(), subtotal);
        BigDecimal deliveryFee = resolveDeliveryFee(merchant, request.deliveryType(), subtotal);
        return new PricedOrder(items, subtotal, deliveryFee, subtotal.add(deliveryFee));
    }

    private void requireMinimumBasket(Merchant merchant, StoreDeliveryType deliveryType, BigDecimal subtotal) {
        if (deliveryType != StoreDeliveryType.DELIVERY) {
            return;
        }
        BigDecimal minimum = merchant.getMinOrderAmount();
        if (minimum != null && subtotal.compareTo(minimum) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Minimum sepet tutarı " + minimum + " " + merchant.getCurrency()
            );
        }
    }

    private BigDecimal resolveDeliveryFee(Merchant merchant, StoreDeliveryType deliveryType, BigDecimal subtotal) {
        if (deliveryType != StoreDeliveryType.DELIVERY) {
            return BigDecimal.ZERO;
        }
        BigDecimal threshold = merchant.getFreeDeliveryThreshold();
        if (threshold != null && subtotal.compareTo(threshold) >= 0) {
            return BigDecimal.ZERO;
        }
        return merchant.getDeliveryFee() == null ? BigDecimal.ZERO : merchant.getDeliveryFee();
    }

    private List<SelectedMenuOption> resolveOptions(
            StorePublicDtos.OrderItemRequest requested,
            List<MenuDtos.MenuProductOptionGroupResponse> groups
    ) {
        if (requested.options() == null || requested.options().isEmpty()) {
            return List.of();
        }
        Map<Long, MenuDtos.MenuProductOptionGroupResponse> groupsById = indexGroups(groups);
        List<SelectedMenuOption> selected = new ArrayList<>();
        for (StorePublicDtos.SelectedOptionRequest option : requested.options()) {
            MenuDtos.MenuProductOptionGroupResponse group = groupsById.get(option.groupId());
            if (group == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçersiz ürün seçeneği");
            }
            MenuDtos.MenuProductOptionResponse match = group.getOptions().stream()
                    .filter(candidate -> candidate.getOptionId().equals(option.optionId()) && candidate.isAvailable())
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçersiz ürün seçeneği"));
            selected.add(SelectedMenuOption.builder()
                    .groupId(group.getGroupId())
                    .groupName(group.getName())
                    .optionId(match.getOptionId())
                    .optionName(match.getName())
                    .priceDelta(match.getPriceDelta() == null ? BigDecimal.ZERO : match.getPriceDelta())
                    .build());
        }
        return selected;
    }

    private Map<Long, MenuDtos.MenuProductOptionGroupResponse> indexGroups(
            List<MenuDtos.MenuProductOptionGroupResponse> groups
    ) {
        Map<Long, MenuDtos.MenuProductOptionGroupResponse> byId = new LinkedHashMap<>();
        if (groups != null) {
            groups.forEach(group -> byId.put(group.getGroupId(), group));
        }
        return byId;
    }

    private BigDecimal optionsTotal(List<SelectedMenuOption> selected) {
        return selected.stream()
                .map(SelectedMenuOption::getPriceDelta)
                .filter(delta -> delta != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal basePrice(MenuProduct product) {
        return product.getPrice() == null ? BigDecimal.ZERO : product.getPrice();
    }
}
