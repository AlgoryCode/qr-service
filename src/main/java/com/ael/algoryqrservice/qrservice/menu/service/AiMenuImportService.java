package com.ael.algoryqrservice.qrservice.menu.service;

import com.ael.algoryqrservice.qrservice.client.AiServiceClient;
import com.ael.algoryqrservice.qrservice.client.dto.AiMenuImportClientDtos;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.model.Menu;
import com.ael.algoryqrservice.qrservice.model.MenuCategory;
import com.ael.algoryqrservice.qrservice.model.MenuSubCategory;
import com.ael.algoryqrservice.qrservice.menu.dto.AiMenuImportDtos;
import com.ael.algoryqrservice.qrservice.menu.dto.MenuDtos;
import com.ael.algoryqrservice.qrservice.menu.dto.TaxonomyDtos;
import com.ael.algoryqrservice.qrservice.model.enums.NutritionBasis;
import com.ael.algoryqrservice.qrservice.model.nutrition.NutritionFacts;
import com.ael.algoryqrservice.qrservice.repository.MenuRepository;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import com.ael.algoryqrservice.qrservice.menu.service.MenuCategoryService;
import com.ael.algoryqrservice.qrservice.menu.service.MenuService;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiMenuImportService {

    private final MenuRepository menuRepository;
    private final MenuService menuService;
    private final MenuCategoryService menuCategoryService;
    private final AiServiceClient aiServiceClient;
    private final SecurityUtils securityUtils;

    public AiMenuImportDtos.JobAccepted createJob(Long menuId, AiMenuImportDtos.CreateJobRequest request) {
        Menu menu = requireOwnedMenuEntity(menuId);
        List<String> imageUrls = normalizeImageUrls(request.getImageUrls());
        if (imageUrls.isEmpty()) {
            throw new BadRequestException("En az bir görsel URL zorunludur");
        }
        try {
            AiMenuImportClientDtos.JobAccepted remote = aiServiceClient.createMenuImportJob(
                    AiMenuImportClientDtos.CreateRequest.builder()
                            .menuId(menuId)
                            .merchantId(menu.getMerchantId())
                            .imageUrls(imageUrls)
                            .build()
            );
            if (remote == null || remote.getJobId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI menü import başlatılamadı");
            }
            log.info(
                    "menu_import_proxy_accepted jobId={} menuId={} status={}",
                    remote.getJobId(), menuId, remote.getStatus()
            );
            return AiMenuImportDtos.JobAccepted.builder()
                    .jobId(remote.getJobId())
                    .status(remote.getStatus())
                    .build();
        } catch (RestClientResponseException ex) {
            throw mapAiServiceError(ex, "AI menü import başlatılamadı");
        }
    }

    public AiMenuImportDtos.JobResponse getJob(Long menuId, UUID jobId) {
        requireOwnedMenuEntity(menuId);
        try {
            AiMenuImportClientDtos.JobResponse remote = aiServiceClient.getMenuImportJob(jobId);
            if (remote == null || remote.getJobId() == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "AI menu import job bulunamadı");
            }
            if (remote.getMenuId() != null && !menuId.equals(remote.getMenuId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu job bu menüye ait değil");
            }
            return AiMenuImportDtos.JobResponse.builder()
                    .jobId(remote.getJobId())
                    .menuId(remote.getMenuId())
                    .merchantId(remote.getMerchantId())
                    .status(remote.getStatus())
                    .imageUrls(remote.getImageUrls())
                    .publishedCount(remote.getPublishedCount())
                    .productCount(remote.getProductCount())
                    .errorMessage(remote.getErrorMessage())
                    .createdAt(toLocalDateTime(remote.getCreatedAt()))
                    .completedAt(toLocalDateTime(remote.getCompletedAt()))
                    .build();
        } catch (RestClientResponseException ex) {
            throw mapAiServiceError(ex, "AI menu import job okunamadı");
        }
    }

    @Transactional
    public AiMenuImportDtos.PublishResponse publishProducts(AiMenuImportDtos.PublishRequest request) {
        Long menuId = request.getMenuId();
        Long merchantId = request.getMerchantId();
        Menu menu = menuRepository.findById(menuId)
                .filter(item -> !item.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü bulunamadı"));
        if (!merchantId.equals(menu.getMerchantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu menüye erişim yetkiniz yok");
        }

        List<Long> productIds = new ArrayList<>();
        for (AiMenuImportDtos.PublishProduct product : request.getProducts()) {
            if (product == null) {
                continue;
            }
            String name = product.getName() == null ? "" : product.getName().trim();
            if (name.isBlank() || product.getPrice() == null) {
                continue;
            }
            Long subCategoryId = resolveOrCreateSubCategoryId(menuId, product);
            MenuDtos.MenuProductRequest createRequest = MenuDtos.MenuProductRequest.builder()
                    .name(name)
                    .description(product.getDescription())
                    .price(product.getPrice())
                    .currency(product.getCurrency() == null || product.getCurrency().isBlank()
                            ? "TRY"
                            : product.getCurrency())
                    .subCategoryId(subCategoryId)
                    .imageUrl(product.getImageUrl())
                    .available(product.getAvailable() == null || product.getAvailable())
                    .servesPeopleMin(1)
                    .servesPeopleMax(1)
                    .nutrition(normalizeNutrition(product.getNutrition()))
                    .build();
            MenuDtos.MenuProductResponse created = menuService.createProductForOwner(menuId, merchantId, createRequest);
            productIds.add(created.getProductId());
        }
        log.info(
                "menu_import_products_published menuId={} merchantId={} createdCount={}",
                menuId, merchantId, productIds.size()
        );
        return AiMenuImportDtos.PublishResponse.builder()
                .menuId(menuId)
                .createdCount(productIds.size())
                .productIds(productIds)
                .build();
    }

    private Long resolveOrCreateSubCategoryId(Long menuId, AiMenuImportDtos.PublishProduct product) {
        if (product.getSubCategoryId() != null) {
            menuCategoryService.requireSubCategory(menuId, product.getSubCategoryId());
            return product.getSubCategoryId();
        }
        String subcategory = blankToNull(product.getSubcategory());
        String category = blankToNull(product.getCategory());
        Map<Long, MenuSubCategory> subs = menuCategoryService.loadSubCategoryMap(menuId);
        Map<Long, MenuCategory> mains = menuCategoryService.loadCategoryMap(menuId);
        if (subcategory != null) {
            String needle = normalizeName(subcategory);
            for (MenuSubCategory sub : subs.values()) {
                if (normalizeName(sub.getName()).equals(needle)) {
                    return sub.getId();
                }
            }
        }
        if (category != null) {
            String needle = normalizeName(category);
            for (MenuSubCategory sub : subs.values()) {
                MenuCategory main = mains.get(sub.getMenuCategoryId());
                if (main != null && normalizeName(main.getName()).equals(needle)) {
                    return sub.getId();
                }
            }
            for (MenuCategory main : mains.values()) {
                if (normalizeName(main.getName()).equals(needle)) {
                    TaxonomyDtos.SubCategoryResponse created = menuCategoryService.createSub(
                            menuId,
                            main.getId(),
                            TaxonomyDtos.SubCategoryRequest.builder()
                                    .name(subcategory != null ? subcategory : category)
                                    .build()
                    );
                    return created.getId();
                }
            }
        }
        String mainName = category != null ? category : "AI Import";
        String subName = subcategory != null ? subcategory : "Genel";
        Long mainId = null;
        for (MenuCategory main : mains.values()) {
            if (normalizeName(main.getName()).equals(normalizeName(mainName))) {
                mainId = main.getId();
                break;
            }
        }
        if (mainId == null) {
            TaxonomyDtos.MainCategoryResponse createdMain = menuCategoryService.createCategory(
                    menuId,
                    TaxonomyDtos.MainCategoryRequest.builder().name(mainName).build()
            );
            mainId = createdMain.getId();
        }
        TaxonomyDtos.SubCategoryResponse createdSub = menuCategoryService.createSub(
                menuId,
                mainId,
                TaxonomyDtos.SubCategoryRequest.builder().name(subName).build()
        );
        return createdSub.getId();
    }

    private Menu requireOwnedMenuEntity(Long menuId) {
        Long merchantId = securityUtils.getCurrentMerchantId();
        Menu menu = menuRepository.findById(menuId)
                .filter(item -> !item.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü bulunamadı"));
        if (!merchantId.equals(menu.getMerchantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu menüye erişim yetkiniz yok");
        }
        return menu;
    }

    private List<String> normalizeImageUrls(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        return raw.stream()
                .filter(url -> url != null && !url.isBlank())
                .map(String::trim)
                .distinct()
                .limit(20)
                .toList();
    }

    private LocalDateTime toLocalDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException ex) {
                return null;
            }
        }
    }

    private ResponseStatusException mapAiServiceError(RestClientResponseException ex, String fallback) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.BAD_GATEWAY;
        } else if (status.is5xxServerError()) {
            status = HttpStatus.BAD_GATEWAY;
        }
        String detail = ex.getResponseBodyAsString();
        if (detail == null || detail.isBlank()) {
            detail = fallback;
        }
        log.warn(
                "menu_import_proxy_failed status={} body={}",
                ex.getStatusCode().value(),
                detail.length() > 500 ? detail.substring(0, 500) : detail
        );
        return new ResponseStatusException(status, fallback);
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private NutritionFacts normalizeNutrition(NutritionFacts nutrition) {
        if (nutrition == null) {
            return null;
        }
        if (nutrition.getBasis() == null) {
            nutrition.setBasis(NutritionBasis.PER_100G);
        }
        return nutrition;
    }
}
