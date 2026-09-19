package com.ael.algoryqrservice.store.service;

import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.MenuProductOptionGroup;
import com.ael.algoryqrservice.model.SelectedMenuOption;
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
        Map<Long, List<MenuProductOptionGroup>> optionGroups =
                menuProductOptionService.loadEntitiesByProductIds(products.keySet());

        List<StoreOrderItem> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (StorePublicDtos.OrderItemRequest requested : request.items()) {
            MenuProduct product = products.get(requested.productId());
            if (product == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ürün satışta değil");
            }
            List<Long> selectedIds = requested.options() == null
                    ? List.of()
                    : requested.options().stream()
                            .map(StorePublicDtos.SelectedOptionRequest::optionId)
                            .toList();
            List<SelectedMenuOption> selected = menuProductOptionService.resolveSelections(
                    product.getProductId(),
                    optionGroups.get(product.getProductId()),
                    selectedIds
            ).selectedOptions();
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
