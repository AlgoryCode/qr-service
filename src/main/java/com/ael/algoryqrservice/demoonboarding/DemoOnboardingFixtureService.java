package com.ael.algoryqrservice.demoonboarding;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.Branch;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.RestaurantTable;
import com.ael.algoryqrservice.model.dto.QrRequest;
import com.ael.algoryqrservice.model.dto.QrResponse;
import com.ael.algoryqrservice.repository.BranchRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.RestaurantTableRepository;
import com.ael.algoryqrservice.service.BranchQuotaService;
import com.ael.algoryqrservice.service.MenuCatalogCloneService;
import com.ael.algoryqrservice.service.MenuThemeService;
import com.ael.algoryqrservice.service.QrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DemoOnboardingFixtureService {

    private final DemoOnboardingFixtureProperties properties;
    private final DemoOnboardingAssignmentRepository assignmentRepository;
    private final BranchRepository branchRepository;
    private final MenuRepository menuRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final BranchQuotaService branchQuotaService;
    private final QrService qrService;
    private final MenuCatalogCloneService menuCatalogCloneService;
    private final MenuThemeService menuThemeService;
    private final DemoOnboardingSalesSeedService salesSeedService;

    @Transactional
    public void assign(Long userId, String displayName) {
        if (!properties.isReady() || userId == null) {
            return;
        }
        if (userId.equals(properties.getCatalogTemplateUserId())
                || userId.equals(properties.getThemeTemplateUserId())) {
            return;
        }
        if (assignmentRepository.existsByUserId(userId)) {
            return;
        }

        Branch catalogBranch = requireCatalogBranch();
        Menu catalogMenu = requireCatalogMenu();
        Menu themeMenu = requireThemeMenu();

        String themeCode = themeMenu.getThemeId();
        if (themeCode != null && !themeCode.isBlank()) {
            menuThemeService.replaceThemes(userId, List.of(themeCode.trim()), null);
        }

        Branch branch = copyBranch(catalogBranch, userId);
        Menu menu = createMenu(catalogMenu, themeMenu, branch, userId, displayName);
        Map<Long, Long> productMap = menuCatalogCloneService.cloneFromTemplate(
                menu,
                catalogMenu.getMenuId(),
                properties.getCatalogTemplateUserId()
        );
        applyThemePresentation(themeMenu, menu);
        Long tableId = ensureDemoTable(menu.getMenuId());

        int bills = salesSeedService.seedFromTemplateOrSynthetic(
                catalogMenu.getMenuId(),
                menu.getMenuId(),
                tableId,
                productMap,
                userId,
                properties.getSalesBackfillDays(),
                properties.getSalesMaxBills()
        );

        assignmentRepository.save(DemoOnboardingAssignment.builder()
                .userId(userId)
                .branchId(branch.getId())
                .menuId(menu.getMenuId())
                .build());

        log.info(
                "Demo onboarding fixture assigned. userId={} branchId={} menuId={} theme={} products={} bills={}",
                userId,
                branch.getId(),
                menu.getMenuId(),
                themeCode,
                productMap.size(),
                bills
        );
    }

    private Branch requireCatalogBranch() {
        return branchRepository.findByIdAndUserIdAndDeletedFalse(
                        properties.getCatalogTemplateBranchId(),
                        properties.getCatalogTemplateUserId()
                )
                .orElseThrow(() -> new BadRequestException("Demo katalog sablon subesi bulunamadi"));
    }

    private Menu requireCatalogMenu() {
        Menu menu = menuRepository.findById(properties.getCatalogTemplateMenuId())
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new BadRequestException("Demo katalog sablon menusu bulunamadi"));
        if (!properties.getCatalogTemplateUserId().equals(menu.getUserId())) {
            throw new BadRequestException("Demo katalog sablon menusu gecersiz");
        }
        if (!properties.getCatalogTemplateBranchId().equals(menu.getBranchId())) {
            throw new BadRequestException("Demo katalog sablon menusu subeye bagli degil");
        }
        if (!menu.isActive()) {
            throw new BadRequestException("Demo katalog sablon menusu aktif degil");
        }
        return menu;
    }

    private Menu requireThemeMenu() {
        Menu menu = menuRepository.findById(properties.getThemeTemplateMenuId())
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new BadRequestException("Demo tema sablon menusu bulunamadi"));
        if (!properties.getThemeTemplateUserId().equals(menu.getUserId())) {
            throw new BadRequestException("Demo tema sablon menusu gecersiz");
        }
        if (!menu.isActive()) {
            throw new BadRequestException("Demo tema sablon menusu aktif degil");
        }
        return menu;
    }

    private Branch copyBranch(Branch template, Long userId) {
        branchQuotaService.assertCanCreateBranch(userId);
        return branchRepository.save(Branch.builder()
                .userId(userId)
                .name(template.getName())
                .address(template.getAddress())
                .phone(template.getPhone())
                .email(template.getEmail())
                .photoUrl(template.getPhotoUrl())
                .photoKey(template.getPhotoKey())
                .grandfathered(false)
                .active(true)
                .kitchenEnabled(false)
                .printKitchenEnabled(false)
                .build());
    }

    private Menu createMenu(Menu catalogTemplate, Menu themeTemplate, Branch branch, Long userId, String displayName) {
        String businessName = catalogTemplate.getBusinessName();
        if (businessName == null || businessName.isBlank()) {
            businessName = displayName != null && !displayName.isBlank() ? displayName.trim() + " Demo" : "Demo Menu";
        }
        QrRequest request = QrRequest.builder()
                .userId(userId)
                .qrName(businessName)
                .type("menu")
                .details(menuDetails(catalogTemplate, themeTemplate, branch.getId(), businessName))
                .build();
        try {
            QrResponse created = qrService.createQR(request, userId);
            return menuRepository.findById(created.getMenuId())
                    .orElseThrow(() -> new BadRequestException("Demo menusu olusturulamadi"));
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Demo menusu olusturulamadi", exception);
        }
    }

    private void applyThemePresentation(Menu themeTemplate, Menu target) {
        target.setThemeId(themeTemplate.getThemeId());
        target.setLogoUrl(themeTemplate.getLogoUrl());
        target.setLogoKey(themeTemplate.getLogoKey());
        target.setCoverUrl(themeTemplate.getCoverUrl());
        target.setCoverKey(themeTemplate.getCoverKey());
        target.setSlogan(themeTemplate.getSlogan());
        target.setChefName(themeTemplate.getChefName());
        target.setChefAvatarKey(themeTemplate.getChefAvatarKey());
        menuRepository.save(target);
    }

    private Long ensureDemoTable(Long menuId) {
        return restaurantTableRepository
                .findFirstByMenuIdAndActiveTrueAndDeletedFalseOrderByTableNumberAscNameAsc(menuId)
                .map(RestaurantTable::getId)
                .orElseGet(() -> {
                    java.time.LocalDateTime now = java.time.LocalDateTime.now();
                    RestaurantTable table = RestaurantTable.builder()
                            .menuId(menuId)
                            .name("Demo Masa")
                            .tableNumber(1)
                            .capacity(4)
                            .publicToken(UUID.randomUUID().toString().replace("-", ""))
                            .active(true)
                            .deleted(false)
                            .createdAt(now)
                            .updatedAt(now)
                            .build();
                    return restaurantTableRepository.save(table).getId();
                });
    }

    private static Map<String, Object> menuDetails(
            Menu catalogTemplate,
            Menu themeTemplate,
            Long branchId,
            String businessName
    ) {
        Map<String, Object> details = new HashMap<>();
        details.put("themeId", themeTemplate.getThemeId());
        details.put("businessName", businessName);
        details.put("branchId", branchId);
        details.put("slogan", themeTemplate.getSlogan() != null ? themeTemplate.getSlogan() : catalogTemplate.getSlogan());
        details.put("chefName", themeTemplate.getChefName());
        details.put("chefAvatarKey", themeTemplate.getChefAvatarKey());
        details.put("phone", catalogTemplate.getPhone());
        details.put("email", catalogTemplate.getEmail());
        details.put("address", catalogTemplate.getAddress());
        return details;
    }

}
