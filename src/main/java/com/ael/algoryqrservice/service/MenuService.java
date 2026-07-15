package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.config.AppProperties;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuProduct;
import com.ael.algoryqrservice.model.Qr;
import com.ael.algoryqrservice.model.UrlMode;
import com.ael.algoryqrservice.model.dto.MenuDtos;
import com.ael.algoryqrservice.model.dto.QrRequest;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.QrRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import com.ael.algoryqrservice.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MenuService {

    private final MenuRepository menuRepository;
    private final MenuProductRepository menuProductRepository;
    private final QrRepository qrRepository;
    private final QrGenerationService qrGenerationService;
    private final AppProperties appProperties;
    private final SecurityUtils securityUtils;

    @Transactional
    public Menu createMenuForQr(Qr qr, QrRequest request) {
        Map<String, Object> details = request.getDetails();
        UrlMode urlMode = UrlMode.from(stringValue(details.get("urlMode")));
        String themeId = requireNonBlank(stringValue(details.get("themeId")), "themeId zorunludur");
        String businessName = requireNonBlank(stringValue(details.get("businessName")), "businessName zorunludur");
        String publicSlug = resolveSlug(urlMode, stringValue(details.get("publicSlug")), null);

        Menu menu = Menu.builder()
                .qrId(qr.getQrId())
                .userId(qr.getUserId())
                .themeId(themeId)
                .businessName(businessName)
                .phone(stringValue(details.get("phone")))
                .email(stringValue(details.get("email")))
                .address(stringValue(details.get("address")))
                .publicSlug(publicSlug)
                .urlMode(urlMode)
                .active(true)
                .build();

        return menuRepository.save(menu);
    }

    public String buildPublicUrl(Menu menu) {
        String base = trimTrailingSlash(appProperties.getUrl());
        if (menu.getUrlMode() == UrlMode.SLUG) {
            return base + "/menu/" + menu.getPublicSlug();
        }
        return base + "/menu/" + menu.getQrId();
    }

    public String buildPublicUrlForMode(UrlMode urlMode, Long qrId, String publicSlug) {
        String base = trimTrailingSlash(appProperties.getUrl());
        if (urlMode == UrlMode.SLUG) {
            return base + "/menu/" + SlugUtils.normalize(publicSlug);
        }
        return base + "/menu/" + qrId;
    }

    @Transactional(readOnly = true)
    public MenuDtos.PublicMenuResponse getPublicMenuByQrId(Long qrId) {
        Menu menu = menuRepository.findByQrIdAndDeletedFalse(qrId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü bulunamadı"));
        return buildPublicResponse(menu);
    }

    @Transactional(readOnly = true)
    public MenuDtos.PublicMenuResponse getPublicMenuBySlug(String slug) {
        String normalized = SlugUtils.normalize(slug);
        Menu menu = menuRepository.findByPublicSlugIgnoreCaseAndDeletedFalse(normalized)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü bulunamadı"));
        return buildPublicResponse(menu);
    }

    @Transactional(readOnly = true)
    public MenuDtos.SlugAvailabilityResponse checkSlugAvailability(String slug, Long excludeMenuId) {
        String normalized = SlugUtils.normalize(slug);
        if (!SlugUtils.isValid(normalized)) {
            return MenuDtos.SlugAvailabilityResponse.builder()
                    .slug(normalized)
                    .available(false)
                    .build();
        }
        boolean available = excludeMenuId == null
                ? !menuRepository.existsByPublicSlugIgnoreCaseAndDeletedFalse(normalized)
                : !menuRepository.existsByPublicSlugIgnoreCaseAndDeletedFalseAndMenuIdNot(normalized, excludeMenuId);
        return MenuDtos.SlugAvailabilityResponse.builder()
                .slug(normalized)
                .available(available)
                .build();
    }

    @Transactional(readOnly = true)
    public List<MenuDtos.MenuProductResponse> listProducts(Long menuId) {
        ensureOwnedMenu(menuId);
        return menuProductRepository.findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(menuId)
                .stream()
                .map(this::toProductResponse)
                .toList();
    }

    @Transactional
    public MenuDtos.MenuProductResponse createProduct(Long menuId, MenuDtos.MenuProductRequest request) {
        Menu menu = ensureOwnedMenu(menuId);
        validateProductRequest(request);

        MenuProduct product = MenuProduct.builder()
                .menuId(menu.getMenuId())
                .name(request.getName().trim())
                .description(trimToNull(request.getDescription()))
                .price(request.getPrice())
                .currency(request.getCurrency() != null && !request.getCurrency().isBlank() ? request.getCurrency().trim() : "TRY")
                .category(trimToNull(request.getCategory()))
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : nextSortOrder(menuId))
                .imageUrl(trimToNull(request.getImageUrl()))
                .available(request.getAvailable() == null || request.getAvailable())
                .build();

        return toProductResponse(menuProductRepository.save(product));
    }

    @Transactional
    public MenuDtos.MenuProductResponse updateProduct(Long productId, MenuDtos.MenuProductRequest request) {
        MenuProduct product = menuProductRepository.findByProductIdAndDeletedFalse(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ürün bulunamadı"));
        ensureOwnedMenu(product.getMenuId());
        validateProductRequest(request);

        product.setName(request.getName().trim());
        product.setDescription(trimToNull(request.getDescription()));
        product.setPrice(request.getPrice());
        if (request.getCurrency() != null && !request.getCurrency().isBlank()) {
            product.setCurrency(request.getCurrency().trim());
        }
        product.setCategory(trimToNull(request.getCategory()));
        if (request.getSortOrder() != null) {
            product.setSortOrder(request.getSortOrder());
        }
        product.setImageUrl(trimToNull(request.getImageUrl()));
        if (request.getAvailable() != null) {
            product.setAvailable(request.getAvailable());
        }

        return toProductResponse(menuProductRepository.save(product));
    }

    @Transactional
    public void deleteProduct(Long productId) {
        MenuProduct product = menuProductRepository.findByProductIdAndDeletedFalse(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ürün bulunamadı"));
        ensureOwnedMenu(product.getMenuId());
        product.setDeleted(true);
        menuProductRepository.save(product);
    }

    @Transactional
    public MenuDtos.MenuProfileResponse updateMenu(Long menuId, MenuDtos.MenuUpdateRequest request) throws Exception {
        Menu menu = ensureOwnedMenu(menuId);
        Qr qr = qrRepository.findById(menu.getQrId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "QR bulunamadı"));

        if (request.getThemeId() != null && !request.getThemeId().isBlank()) {
            menu.setThemeId(request.getThemeId().trim());
        }
        if (request.getBusinessName() != null && !request.getBusinessName().isBlank()) {
            menu.setBusinessName(request.getBusinessName().trim());
        }
        if (request.getPhone() != null) menu.setPhone(trimToNull(request.getPhone()));
        if (request.getEmail() != null) menu.setEmail(trimToNull(request.getEmail()));
        if (request.getAddress() != null) menu.setAddress(trimToNull(request.getAddress()));
        if (request.getActive() != null) menu.setActive(request.getActive());

        UrlMode nextMode = request.getUrlMode() != null ? UrlMode.from(request.getUrlMode()) : menu.getUrlMode();
        String nextSlug = menu.getPublicSlug();
        if (nextMode == UrlMode.SLUG) {
            String candidate = request.getPublicSlug() != null ? request.getPublicSlug() : menu.getPublicSlug();
            nextSlug = resolveSlug(nextMode, candidate, menu.getMenuId());
        } else {
            nextSlug = null;
        }

        menu.setUrlMode(nextMode);
        menu.setPublicSlug(nextSlug);
        menuRepository.save(menu);

        String publicUrl = buildPublicUrl(menu);
        qrGenerationService.updateQrContent(qr, publicUrl);

        return toMenuProfile(menu, publicUrl);
    }

    @Transactional(readOnly = true)
    public MenuDtos.MenuProfileResponse getMenuProfile(Long menuId) {
        Menu menu = ensureOwnedMenu(menuId);
        return toMenuProfile(menu, buildPublicUrl(menu));
    }

    @Transactional(readOnly = true)
    public Menu findByQrId(Long qrId) {
        Menu menu = menuRepository.findByQrIdAndDeletedFalse(qrId).orElse(null);
        if (menu != null) {
            requireOwnership(menu);
        }
        return menu;
    }

    private Menu ensureMenuExists(Long menuId) {
        return menuRepository.findById(menuId)
                .filter(menu -> !menu.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü bulunamadı"));
    }

    private Menu ensureOwnedMenu(Long menuId) {
        Menu menu = ensureMenuExists(menuId);
        requireOwnership(menu);
        return menu;
    }

    private void requireOwnership(Menu menu) {
        Long currentUserId = securityUtils.getCurrentUser().getId();
        if (!currentUserId.equals(menu.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu menüye erişim yetkiniz yok");
        }
    }

    private int nextSortOrder(Long menuId) {
        return menuProductRepository.findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(menuId).size();
    }

    private MenuDtos.PublicMenuResponse buildPublicResponse(Menu menu) {
        if (!menu.isActive()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Menü yayında değil");
        }
        List<MenuDtos.MenuProductResponse> products = menuProductRepository
                .findByMenuIdAndDeletedFalseOrderBySortOrderAscProductIdAsc(menu.getMenuId())
                .stream()
                .filter(MenuProduct::isAvailable)
                .map(this::toProductResponse)
                .toList();

        return MenuDtos.PublicMenuResponse.builder()
                .menu(toMenuProfile(menu, buildPublicUrl(menu)))
                .products(products)
                .themeId(menu.getThemeId())
                .build();
    }

    private MenuDtos.MenuProfileResponse toMenuProfile(Menu menu, String publicUrl) {
        return MenuDtos.MenuProfileResponse.builder()
                .menuId(menu.getMenuId())
                .qrId(menu.getQrId())
                .userId(menu.getUserId())
                .themeId(menu.getThemeId())
                .businessName(menu.getBusinessName())
                .phone(menu.getPhone())
                .email(menu.getEmail())
                .address(menu.getAddress())
                .publicSlug(menu.getPublicSlug())
                .urlMode(menu.getUrlMode().name())
                .publicUrl(publicUrl)
                .active(menu.isActive())
                .build();
    }

    private MenuDtos.MenuProductResponse toProductResponse(MenuProduct product) {
        return MenuDtos.MenuProductResponse.builder()
                .productId(product.getProductId())
                .menuId(product.getMenuId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .currency(product.getCurrency())
                .category(product.getCategory())
                .sortOrder(product.getSortOrder())
                .imageUrl(product.getImageUrl())
                .available(product.isAvailable())
                .build();
    }

    private void validateProductRequest(MenuDtos.MenuProductRequest request) {
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ürün adı zorunludur");
        }
    }

    private String resolveSlug(UrlMode urlMode, String rawSlug, Long excludeMenuId) {
        if (urlMode != UrlMode.SLUG) {
            return null;
        }
        String normalized = SlugUtils.normalize(rawSlug);
        if (!SlugUtils.isValid(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçersiz slug formatı");
        }
        boolean taken = excludeMenuId == null
                ? menuRepository.existsByPublicSlugIgnoreCaseAndDeletedFalse(normalized)
                : menuRepository.existsByPublicSlugIgnoreCaseAndDeletedFalseAndMenuIdNot(normalized, excludeMenuId);
        if (taken) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu slug zaten kullanılıyor");
        }
        return normalized;
    }

    private String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/$", "");
    }
}
