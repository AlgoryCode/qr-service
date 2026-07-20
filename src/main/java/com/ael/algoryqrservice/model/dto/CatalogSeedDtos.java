package com.ael.algoryqrservice.model.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class CatalogSeedDtos {
    private CatalogSeedDtos() {
    }

    @Data
    public static class Document {
        private List<ProductSeed> products = new ArrayList<>();
        private List<PackageSeed> packages = new ArrayList<>();
    }

    @Data
    public static class ProductSeed {
        private String code;
        private String name;
        private String description;
        private String scopeCode;
        private BigDecimal unitPrice;
        private BigDecimal vatRate;
        private Boolean countable;
        private Boolean active;
    }

    @Data
    public static class PackageSeed {
        private String code;
        private String name;
        private String description;
        private List<String> features = new ArrayList<>();
        private String currency;
        private Integer validityDays;
        private Integer priority;
        private Boolean purchasable;
        private Boolean systemManaged;
        private Boolean trialEligible;
        private Boolean active;
        private BigDecimal lockPrice;
        private List<ItemSeed> items = new ArrayList<>();
    }

    @Data
    public static class ItemSeed {
        private String productCode;
        private Integer quantity;
        private Boolean unlimited;
    }

    @Data
    public static class ImportResult {
        private int productsUpserted;
        private int packagesUpserted;
        private List<String> packageCodes = new ArrayList<>();
    }
}
