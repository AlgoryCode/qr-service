package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogPackages;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.PlanPackage;
import com.ael.algoryqrservice.model.PlanPackageItem;
import com.ael.algoryqrservice.model.Product;
import com.ael.algoryqrservice.model.dto.CatalogSeedDtos;
import com.ael.algoryqrservice.repository.PlanPackageRepository;
import com.ael.algoryqrservice.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CatalogSeedService {

    private static final String CLASSPATH_SEED = "seed/catalog-tiers.json";

    private final ProductRepository productRepository;
    private final PlanPackageRepository planPackageRepository;
    private final PackagePricingService packagePricingService;
    private final ObjectMapper objectMapper;

    @Transactional
    public CatalogSeedDtos.ImportResult importClasspathSeed() {
        return importDocument(loadClasspathSeed());
    }

    @Transactional
    public CatalogSeedDtos.ImportResult importDocument(CatalogSeedDtos.Document document) {
        if (document == null) {
            throw new BadRequestException("Seed dokumani bos olamaz");
        }
        CatalogSeedDtos.ImportResult result = new CatalogSeedDtos.ImportResult();

        for (CatalogSeedDtos.ProductSeed seed : nullSafe(document.getProducts())) {
            upsertProduct(seed);
            result.setProductsUpserted(result.getProductsUpserted() + 1);
        }

        Map<String, Product> productsByCode = productRepository.findAll().stream()
                .collect(Collectors.toMap(Product::getCode, Function.identity(), (a, b) -> a));

        for (CatalogSeedDtos.PackageSeed seed : nullSafe(document.getPackages())) {
            PlanPackage planPackage = upsertPackage(seed, productsByCode);
            result.setPackagesUpserted(result.getPackagesUpserted() + 1);
            result.getPackageCodes().add(planPackage.getCode());
        }
        return result;
    }

    public CatalogSeedDtos.Document loadClasspathSeed() {
        try {
            ClassPathResource resource = new ClassPathResource(CLASSPATH_SEED);
            try (InputStream inputStream = resource.getInputStream()) {
                return objectMapper.readValue(inputStream, CatalogSeedDtos.Document.class);
            }
        } catch (Exception exception) {
            throw new BadRequestException("Classpath seed okunamadi: " + exception.getMessage());
        }
    }

    private void upsertProduct(CatalogSeedDtos.ProductSeed seed) {
        if (seed.getCode() == null || seed.getCode().isBlank()) {
            throw new BadRequestException("Urun kodu zorunludur");
        }
        if (seed.getName() == null || seed.getName().isBlank()) {
            throw new BadRequestException("Urun adi zorunludur: " + seed.getCode());
        }
        String code = seed.getCode().trim().toUpperCase();
        Product product = productRepository.findByCode(code).orElseGet(Product::new);
        product.setCode(code);
        product.setName(seed.getName().trim());
        product.setDescription(trimToNull(seed.getDescription()));
        product.setScopeCode(seed.getScopeCode() == null || seed.getScopeCode().isBlank()
                ? code + "_OWNER"
                : seed.getScopeCode().trim());
        product.setUnitPrice(seed.getUnitPrice() == null ? BigDecimal.ZERO : seed.getUnitPrice());
        product.setVatRate(seed.getVatRate() == null ? new BigDecimal("20.00") : seed.getVatRate());
        product.setConsumable(seed.getCountable() == null || Boolean.TRUE.equals(seed.getCountable()));
        product.setActive(seed.getActive() == null || Boolean.TRUE.equals(seed.getActive()));
        productRepository.save(product);
    }

    private PlanPackage upsertPackage(CatalogSeedDtos.PackageSeed seed, Map<String, Product> productsByCode) {
        if (seed.getCode() == null || seed.getCode().isBlank()) {
            throw new BadRequestException("Paket kodu zorunludur");
        }
        if (seed.getName() == null || seed.getName().isBlank()) {
            throw new BadRequestException("Paket adi zorunludur: " + seed.getCode());
        }
        String code = seed.getCode().trim().toUpperCase();
        PlanPackage planPackage = planPackageRepository.findByCode(code)
                .flatMap(existing -> planPackageRepository.findByIdWithItems(existing.getId()))
                .orElseGet(() -> PlanPackage.builder()
                        .code(code)
                        .items(new ArrayList<>())
                        .features(new ArrayList<>())
                        .build());

        planPackage.setName(seed.getName().trim());
        planPackage.setDescription(trimToNull(seed.getDescription()));
        planPackage.setFeatures(normalizeFeatures(seed.getFeatures()));
        planPackage.setCurrency(seed.getCurrency() == null || seed.getCurrency().isBlank()
                ? "TRY"
                : seed.getCurrency().trim().toUpperCase());
        planPackage.setValidityDays(seed.getValidityDays() == null ? 30 : seed.getValidityDays());
        planPackage.setPriority(seed.getPriority() == null ? 0 : seed.getPriority());
        planPackage.setPurchasable(Boolean.TRUE.equals(seed.getPurchasable()));
        planPackage.setSystemManaged(Boolean.TRUE.equals(seed.getSystemManaged())
                || CatalogPackages.FREE_PACKAGE.equals(code));
        planPackage.setTrialEligible(Boolean.TRUE.equals(seed.getTrialEligible()));
        planPackage.setActive(seed.getActive() == null || Boolean.TRUE.equals(seed.getActive()));
        if (planPackage.getPrice() == null) {
            planPackage.setPrice(BigDecimal.ZERO);
        }
        if (planPackage.getSubtotal() == null) {
            planPackage.setSubtotal(BigDecimal.ZERO);
        }
        if (planPackage.getVatAmount() == null) {
            planPackage.setVatAmount(BigDecimal.ZERO);
        }

        syncItems(planPackage, nullSafe(seed.getItems()), productsByCode);
        packagePricingService.applyTo(planPackage);

        if (CatalogPackages.FREE_PACKAGE.equals(code) || planPackage.isSystemManaged()) {
            planPackage.setPrice(BigDecimal.ZERO);
            planPackage.setSubtotal(BigDecimal.ZERO);
            planPackage.setVatAmount(BigDecimal.ZERO);
            planPackage.setPurchasable(false);
            planPackage.setTrialEligible(false);
        } else if (seed.getLockPrice() != null) {
            planPackage.setPrice(seed.getLockPrice());
        }

        return planPackageRepository.save(planPackage);
    }

    private void syncItems(
            PlanPackage planPackage,
            List<CatalogSeedDtos.ItemSeed> itemSeeds,
            Map<String, Product> productsByCode
    ) {
        Set<String> requestedCodes = new HashSet<>();
        for (CatalogSeedDtos.ItemSeed itemSeed : itemSeeds) {
            if (itemSeed.getProductCode() == null || itemSeed.getProductCode().isBlank()) {
                throw new BadRequestException("Paket item productCode zorunludur: " + planPackage.getCode());
            }
            String productCode = itemSeed.getProductCode().trim().toUpperCase();
            if (!requestedCodes.add(productCode)) {
                throw new BadRequestException("Ayni urun pakette tekrarlanamaz: " + productCode);
            }
            Product product = productsByCode.get(productCode);
            if (product == null) {
                throw new BadRequestException("Urun bulunamadi: " + productCode);
            }
            boolean unlimited = Boolean.TRUE.equals(itemSeed.getUnlimited());
            int quantity = unlimited ? 0 : (itemSeed.getQuantity() == null ? 1 : itemSeed.getQuantity());
            if (!unlimited && quantity < 1) {
                throw new BadRequestException("Miktar en az 1 olmalidir: " + productCode);
            }

            PlanPackageItem existing = planPackage.getItems().stream()
                    .filter(item -> item.getProduct().getCode().equals(productCode))
                    .findFirst()
                    .orElse(null);
            if (existing != null) {
                existing.setQuantity(quantity);
                existing.setUnlimited(unlimited);
            } else {
                planPackage.getItems().add(PlanPackageItem.builder()
                        .planPackage(planPackage)
                        .product(product)
                        .quantity(quantity)
                        .unlimited(unlimited)
                        .build());
            }
        }
        planPackage.getItems().removeIf(item -> !requestedCodes.contains(item.getProduct().getCode()));
    }

    private List<String> normalizeFeatures(List<String> features) {
        if (features == null || features.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> normalized = new ArrayList<>();
        for (String feature : features) {
            if (feature == null || feature.isBlank()) {
                continue;
            }
            String trimmed = feature.trim();
            if (trimmed.length() > 200) {
                throw new BadRequestException("Ozellik metni en fazla 200 karakter olabilir");
            }
            normalized.add(trimmed);
            if (normalized.size() > 30) {
                throw new BadRequestException("En fazla 30 ozellik eklenebilir");
            }
        }
        return normalized;
    }

    private static <T> List<T> nullSafe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
