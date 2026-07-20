package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogCodeFactory;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.dto.ProductRequest;
import com.ael.algoryqrservice.model.dto.ProductResponse;
import com.ael.algoryqrservice.repository.PlanPackageItemRepository;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.ProductRepository;
import com.ael.algoryqrservice.repository.UserEntitlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final PlanPackageItemRepository planPackageItemRepository;
    private final PlanPackageRepository planPackageRepository;
    private final UserEntitlementRepository userEntitlementRepository;
    private final CatalogCodeFactory catalogCodeFactory;
    private final PackagePricingService packagePricingService;

    @Transactional
    public ProductResponse create(ProductRequest request) {
        String code = catalogCodeFactory.resolveUnique(
                request.getCode(),
                request.getName(),
                productRepository::existsByCode
        );
        String scopeCode = resolveScopeCode(request.getScopeCode(), code);

        Product product = Product.builder()
                .code(code)
                .name(request.getName().trim())
                .description(trimToNull(request.getDescription()))
                .scopeCode(scopeCode)
                .unitPrice(request.resolvedUnitPrice())
                .vatRate(request.resolvedVatRate())
                .consumable(request.resolvedCountable())
                .active(request.resolvedActive())
                .build();

        return toResponse(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> getAll() {
        return productRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        return toResponse(findProduct(id));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = findProduct(id);
        product.setName(request.getName().trim());
        product.setDescription(trimToNull(request.getDescription()));
        product.setUnitPrice(request.resolvedUnitPrice());
        product.setVatRate(request.resolvedVatRate());
        product.setConsumable(request.resolvedCountable());
        product.setActive(request.resolvedActive());
        if (request.getScopeCode() != null && !request.getScopeCode().isBlank()) {
            product.setScopeCode(catalogCodeFactory.normalize(request.getScopeCode()));
        }
        Product saved = productRepository.save(product);
        recalculatePackagesContaining(id);
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        Product product = findProduct(id);
        if (userEntitlementRepository.existsByProductId(id)) {
            throw new BadRequestException(
                    "Urun kullanici haklarinda kullanildigi icin silinemez. Once paketlerden cikarin veya urunu pasif yapin: "
                            + product.getCode()
            );
        }
        if (planPackageItemRepository.existsByProductId(id)) {
            List<Long> packageIds = planPackageItemRepository.findByProductId(id).stream()
                    .map(item -> item.getPlanPackage().getId())
                    .distinct()
                    .toList();
            planPackageItemRepository.deleteByProductId(id);
            for (Long packageId : packageIds) {
                planPackageRepository.findByIdWithItems(packageId).ifPresent(pkg -> {
                    if (!pkg.isSystemManaged()) {
                        packagePricingService.applyTo(pkg);
                        planPackageRepository.save(pkg);
                    }
                });
            }
        }
        productRepository.delete(product);
    }

    Product findProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Urun bulunamadi: " + id));
    }

    Product findActiveProduct(Long id) {
        Product product = findProduct(id);
        if (!product.isActive()) {
            throw new BadRequestException("Urun aktif degil: " + id);
        }
        return product;
    }

    private void recalculatePackagesContaining(Long productId) {
        List<PlanPackageItem> items = planPackageItemRepository.findByProductId(productId);
        items.stream()
                .map(item -> item.getPlanPackage().getId())
                .distinct()
                .forEach(packageId -> planPackageRepository.findByIdWithItems(packageId).ifPresent(pkg -> {
                    if (!pkg.isSystemManaged()) {
                        packagePricingService.applyTo(pkg);
                        planPackageRepository.save(pkg);
                    }
                }));
    }

    private String resolveScopeCode(String requestedScope, String code) {
        if (requestedScope != null && !requestedScope.isBlank()) {
            return catalogCodeFactory.normalize(requestedScope);
        }
        return code + "_OWNER";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .code(product.getCode())
                .name(product.getName())
                .description(product.getDescription())
                .scopeCode(product.getScopeCode())
                .unitPrice(product.getUnitPrice())
                .vatRate(product.getVatRate())
                .countable(product.isConsumable())
                .consumable(product.isConsumable())
                .active(product.isActive())
                .createdAt(product.getCreatedAt())
                .build();
    }
}
