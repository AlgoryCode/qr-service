package com.ael.algoryqrservice.stage;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.Branch;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.dto.QrRequest;
import com.ael.algoryqrservice.model.dto.QrResponse;
import com.ael.algoryqrservice.repository.BranchRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.service.BranchQuotaService;
import com.ael.algoryqrservice.service.MenuCatalogCloneService;
import com.ael.algoryqrservice.service.QrService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StageTrialFixtureService {

    private final StageTrialFixtureProperties properties;
    private final StageTrialAssignmentRepository assignmentRepository;
    private final BranchRepository branchRepository;
    private final MenuRepository menuRepository;
    private final BranchQuotaService branchQuotaService;
    private final QrService qrService;
    private final MenuCatalogCloneService menuCatalogCloneService;

    @Transactional
    public void assign(Long userId) {
        if (!properties.isReady() || userId == null || userId.equals(properties.getTemplateUserId())) {
            return;
        }
        if (assignmentRepository.existsByUserId(userId)) {
            return;
        }
        Branch templateBranch = requireTemplateBranch();
        Menu templateMenu = requireTemplateMenu();
        Branch branch = copyBranch(templateBranch, userId);
        Menu menu = createMenu(templateMenu, branch, userId);
        menuCatalogCloneService.cloneFromTemplate(menu, templateMenu.getMenuId(), properties.getTemplateUserId());
        copyMenuMedia(templateMenu, menu);
        assignmentRepository.save(StageTrialAssignment.builder()
                .userId(userId)
                .branchId(branch.getId())
                .menuId(menu.getMenuId())
                .build());
        log.info("Stage trial fixture assigned. userId={} branchId={} menuId={}", userId, branch.getId(), menu.getMenuId());
    }

    private Branch requireTemplateBranch() {
        return branchRepository.findByIdAndUserIdAndDeletedFalse(
                        properties.getTemplateBranchId(),
                        properties.getTemplateUserId()
                )
                .orElseThrow(() -> new BadRequestException("Şablon şube bulunamadı"));
    }

    private Menu requireTemplateMenu() {
        Menu menu = menuRepository.findById(properties.getTemplateMenuId())
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new BadRequestException("Şablon menü bulunamadı"));
        if (!properties.getTemplateUserId().equals(menu.getUserId())) {
            throw new BadRequestException("Şablon menü bu kullanıcıya ait değil");
        }
        if (!properties.getTemplateBranchId().equals(menu.getBranchId())) {
            throw new BadRequestException("Şablon menü şubeye bağlı değil");
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

    private Menu createMenu(Menu template, Branch branch, Long userId) {
        QrRequest request = QrRequest.builder()
                .userId(userId)
                .qrName(template.getBusinessName())
                .type("menu")
                .details(menuDetails(template, branch.getId()))
                .build();
        try {
            QrResponse created = qrService.createQR(request, userId);
            return menuRepository.findById(created.getMenuId())
                    .orElseThrow(() -> new BadRequestException("Kopya menü bulunamadı"));
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Şablon menü kopyalanamadı", exception);
        }
    }

    private void copyMenuMedia(Menu template, Menu target) {
        target.setLogoUrl(template.getLogoUrl());
        target.setLogoKey(template.getLogoKey());
        target.setCoverUrl(template.getCoverUrl());
        target.setCoverKey(template.getCoverKey());
        menuRepository.save(target);
    }

    private static Map<String, Object> menuDetails(Menu template, Long branchId) {
        Map<String, Object> details = new HashMap<>();
        details.put("themeId", template.getThemeId());
        details.put("businessName", template.getBusinessName());
        details.put("branchId", branchId);
        details.put("slogan", template.getSlogan());
        details.put("chefName", template.getChefName());
        details.put("chefAvatarKey", template.getChefAvatarKey());
        details.put("phone", template.getPhone());
        details.put("email", template.getEmail());
        details.put("address", template.getAddress());
        return details;
    }
}
