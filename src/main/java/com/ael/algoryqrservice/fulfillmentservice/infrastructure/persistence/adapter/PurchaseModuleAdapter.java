package com.ael.algoryqrservice.fulfillmentservice.infrastructure.persistence.adapter;

import com.ael.algoryqrservice.fulfillmentservice.domain.model.AddonGrant;
import com.ael.algoryqrservice.fulfillmentservice.domain.port.PurchaseModulePort;
import com.ael.algoryqrservice.qrservice.model.Product;
import com.ael.algoryqrservice.qrservice.model.PurchaseItem;
import com.ael.algoryqrservice.qrservice.product.repository.ProductRepository;
import com.ael.algoryqrservice.qrservice.repository.PurchaseItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PurchaseModuleAdapter implements PurchaseModulePort {

    private final PurchaseItemRepository purchaseItemRepository;
    private final ProductRepository productRepository;

    @Override
    public List<AddonGrant.Line> modulesOf(Long purchaseId) {
        if (purchaseId == null || purchaseId <= 0) {
            return List.of();
        }
        List<PurchaseItem> items = purchaseItemRepository.findByPurchaseId(purchaseId);
        if (items.isEmpty()) {
            return List.of();
        }
        Map<Long, Product> products = productRepository.findAllById(
                        items.stream().map(PurchaseItem::getProductId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        return items.stream()
                .filter(item -> products.containsKey(item.getProductId()))
                .filter(item -> item.getQuantity() != null && item.getQuantity() > 0)
                .map(item -> {
                    Product product = products.get(item.getProductId());
                    String featureCode = product.getFeatureCode() != null && !product.getFeatureCode().isBlank()
                            ? product.getFeatureCode()
                            : product.getCode();
                    return new AddonGrant.Line(
                            product.getId(), featureCode, product.getScopeCode(), item.getQuantity(), null);
                })
                .toList();
    }
}
