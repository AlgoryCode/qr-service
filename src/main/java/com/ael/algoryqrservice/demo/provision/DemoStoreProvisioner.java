package com.ael.algoryqrservice.demo.provision;

import com.ael.algoryqrservice.demoonboarding.DemoOnboardingAssignment;
import com.ael.algoryqrservice.demoonboarding.DemoOnboardingAssignmentRepository;
import com.ael.algoryqrservice.demoonboarding.DemoOnboardingSalesSeedService;
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
import com.ael.algoryqrservice.service.MenuThemeService;
import com.ael.algoryqrservice.service.QrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DemoStoreProvisioner {

    private final DemoTemplateProperties template;
    private final DemoMenuPresentationFixture presentationFixture;
    private final DemoThemePicker themePicker;
    private final DemoProductCatalogSeeder productCatalogSeeder;
    private final DemoOnboardingAssignmentRepository assignmentRepository;
    private final BranchRepository branchRepository;
    private final MenuRepository menuRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final BranchQuotaService branchQuotaService;
    private final QrService qrService;
    private final MenuThemeService menuThemeService;
    private final DemoOnboardingSalesSeedService salesSeedService;

    @Transactional
    public void provisionStore(Long userId, String displayName) {
        if (!template.isReady() || userId == null) {
            return;
        }
        if (assignmentRepository.existsByUserId(userId)) {
            return;
        }

        DemoProvisionContext ctx = new DemoProvisionContext(userId, displayName);
        ctx.setPresentation(presentationFixture.get());
        ctx.setThemeCode(themePicker.pickThemeCode(ctx.getPresentation()));
        createBranch(ctx);
        ensureThemeAssigned(ctx);
        createMenu(ctx);
        defineProducts(ctx);
        applyTheme(ctx);
        seedSales(ctx);
        recordAssignment(ctx);

        log.info(
                "Demo store provisioned. userId={} branchId={} menuId={} theme={} products={} bills={}",
                userId,
                ctx.getBranch().getId(),
                ctx.getMenu().getMenuId(),
                ctx.getThemeCode(),
                ctx.getSeededProductCount(),
                ctx.getSeededBillCount()
        );
    }

    void ensureThemeAssigned(DemoProvisionContext ctx) {
        String themeCode = ctx.getThemeCode();
        if (themeCode == null || themeCode.isBlank()) {
            menuThemeService.ensureDefaultThemeAssigned(ctx.getUserId());
            return;
        }
        menuThemeService.replaceThemes(ctx.getUserId(), List.of(themeCode.trim()), null);
    }

    void createBranch(DemoProvisionContext ctx) {
        DemoMenuPresentation p = ctx.getPresentation();
        branchQuotaService.assertCanCreateBranch(ctx.getUserId());
        String branchName = nonBlank(p.branchName(), DemoProvisionContext.BRANCH_NAME);
        Branch branch = branchRepository.save(Branch.builder()
                .userId(ctx.getUserId())
                .name(branchName)
                .address(p.address())
                .phone(p.phone())
                .email(p.email())
                .grandfathered(false)
                .active(true)
                .kitchenEnabled(false)
                .printKitchenEnabled(false)
                .build());
        ctx.setBranch(branch);
    }

    void createMenu(DemoProvisionContext ctx) {
        DemoMenuPresentation p = ctx.getPresentation();
        String businessName = nonBlank(p.menuBusinessName(), DemoProvisionContext.MENU_BUSINESS_NAME);
        QrRequest request = QrRequest.builder()
                .userId(ctx.getUserId())
                .qrName(businessName)
                .type("menu")
                .details(menuDetails(ctx, businessName))
                .build();
        try {
            QrResponse created = qrService.createQR(request, ctx.getUserId());
            Menu menu = menuRepository.findById(created.getMenuId())
                    .orElseThrow(() -> new BadRequestException("Demo menusu olusturulamadi"));
            ctx.setMenu(menu);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Demo menusu olusturulamadi", exception);
        }
    }

    void defineProducts(DemoProvisionContext ctx) {
        int count = productCatalogSeeder.seedCatalog(ctx.getMenu().getMenuId(), ctx.getUserId());
        ctx.setSeededProductCount(count);
        ctx.setDemoTableId(ensureDemoTable(ctx.getMenu().getMenuId()));
    }

    void applyTheme(DemoProvisionContext ctx) {
        DemoMenuPresentation p = ctx.getPresentation();
        String themeCode = ctx.getThemeCode();
        menuThemeService.replaceThemes(ctx.getUserId(), List.of(themeCode), null);
        Menu target = ctx.getMenu();
        target.setThemeId(themeCode);
        target.setSlogan(p.slogan());
        target.setChefName(p.chefName());
        target.setChefAvatarKey(p.chefAvatarKey());
        if (p.logoUrl() != null && !p.logoUrl().isBlank()) {
            target.setLogoUrl(p.logoUrl());
            target.setLogoKey(p.logoKey());
        }
        if (p.coverUrl() != null && !p.coverUrl().isBlank()) {
            target.setCoverUrl(p.coverUrl());
            target.setCoverKey(p.coverKey());
        }
        menuRepository.save(target);
    }

    void seedSales(DemoProvisionContext ctx) {
        int bills = salesSeedService.seedSyntheticSales(
                ctx.getMenu().getMenuId(),
                ctx.getDemoTableId(),
                ctx.getUserId(),
                template.getSalesBackfillDays(),
                template.getSalesMaxBills()
        );
        ctx.setSeededBillCount(bills);
    }

    void recordAssignment(DemoProvisionContext ctx) {
        if (assignmentRepository.existsByUserId(ctx.getUserId())) {
            return;
        }
        try {
            assignmentRepository.save(DemoOnboardingAssignment.builder()
                    .userId(ctx.getUserId())
                    .branchId(ctx.getBranch().getId())
                    .menuId(ctx.getMenu().getMenuId())
                    .build());
        } catch (DataIntegrityViolationException exception) {
            if (!assignmentRepository.existsByUserId(ctx.getUserId())) {
                throw exception;
            }
        }
    }

    private Long ensureDemoTable(Long menuId) {
        return restaurantTableRepository
                .findFirstByMenuIdAndActiveTrueAndDeletedFalseOrderByTableNumberAscNameAsc(menuId)
                .map(RestaurantTable::getId)
                .orElseGet(() -> {
                    LocalDateTime now = LocalDateTime.now();
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

    private static Map<String, Object> menuDetails(DemoProvisionContext ctx, String businessName) {
        DemoMenuPresentation p = ctx.getPresentation();
        Map<String, Object> details = new HashMap<>();
        details.put("themeId", ctx.getThemeCode());
        details.put("businessName", businessName);
        details.put("branchId", ctx.getBranch().getId());
        details.put("slogan", p.slogan());
        details.put("chefName", p.chefName());
        details.put("chefAvatarKey", p.chefAvatarKey());
        details.put("phone", p.phone());
        details.put("email", p.email());
        details.put("address", p.address());
        return details;
    }

    private static String nonBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
