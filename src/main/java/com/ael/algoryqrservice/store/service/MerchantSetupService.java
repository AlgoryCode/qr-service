package com.ael.algoryqrservice.store.service;

import com.ael.algoryqrservice.model.Branch;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.enums.MenuChannel;
import com.ael.algoryqrservice.repository.BranchRepository;
import com.ael.algoryqrservice.repository.MenuProductRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.service.MenuCatalogCloneService;
import com.ael.algoryqrservice.service.MenuPublicIdGenerator;
import com.ael.algoryqrservice.store.model.Merchant;
import com.ael.algoryqrservice.store.model.MerchantStatus;
import com.ael.algoryqrservice.store.model.dto.StoreDtos;
import com.ael.algoryqrservice.store.repository.MerchantRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MerchantSetupService {

    private static final String STORE_THEME_ID = "store";

    private final MerchantRepository merchantRepository;
    private final MenuRepository menuRepository;
    private final MenuProductRepository menuProductRepository;
    private final BranchRepository branchRepository;
    private final MenuPublicIdGenerator menuPublicIdGenerator;
    private final MenuCatalogCloneService menuCatalogCloneService;
    private final MerchantMapper merchantMapper;
    private final StoreSlugGenerator storeSlugGenerator;
    private final StoreTokenGenerator storeTokenGenerator;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public StoreDtos.SetupPrefillResponse prefill() {
        Long userId = securityUtils.getCurrentUserId();
        List<Branch> branches = branchRepository.findByUserIdAndDeletedFalseOrderByIdDesc(userId);
        List<Menu> menus = cloneableMenus(userId);
        Map<Long, String> branchNames = branches.stream()
                .collect(Collectors.toMap(Branch::getId, Branch::getName, (left, right) -> left));
        return StoreDtos.SetupPrefillResponse.builder()
                .alreadySetUp(merchantRepository.existsByUserIdAndDeletedFalse(userId))
                .suggested(suggestBusinessInfo(branches, menus))
                .branches(branches.stream().map(this::toBranchOption).toList())
                .menus(menus.stream().map(menu -> toMenuOption(menu, branchNames)).toList())
                .build();
    }

    @Transactional
    public StoreDtos.MerchantResponse setup(StoreDtos.SetupRequest request) {
        Long userId = securityUtils.getCurrentUserId();
        if (merchantRepository.existsByUserIdAndDeletedFalse(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mağaza kurulumu zaten tamamlanmış");
        }
        Long branchId = resolveBranchId(request.branchId(), userId);

        Menu catalogMenu = menuRepository.save(Menu.builder()
                .publicId(menuPublicIdGenerator.generateUnique())
                .channel(MenuChannel.STORE)
                .userId(userId)
                .branchId(branchId)
                .themeId(STORE_THEME_ID)
                .businessName(request.business().businessName().trim())
                .phone(request.business().phone())
                .email(request.business().email())
                .address(request.business().address())
                .active(true)
                .build());

        Merchant merchant = Merchant.builder()
                .userId(userId)
                .branchId(branchId)
                .catalogMenuId(catalogMenu.getMenuId())
                .storeNo(merchantRepository.nextStoreNo())
                .slug(storeSlugGenerator.generate(request.business().businessName()))
                .publicToken(storeTokenGenerator.generateUnique(merchantRepository::existsByPublicToken))
                .status(MerchantStatus.ACTIVE)
                .businessName(request.business().businessName().trim())
                .build();
        merchantMapper.applyBusinessInfo(merchant, request.business());
        merchantMapper.applyDeliverySettings(merchant, request.delivery());

        if (request.sourceMenuId() != null) {
            menuCatalogCloneService.cloneInto(catalogMenu, request.sourceMenuId(), userId);
        }
        return merchantMapper.toResponse(merchantRepository.save(merchant));
    }

    private Long resolveBranchId(Long branchId, Long userId) {
        if (branchId == null) {
            return null;
        }
        return branchRepository.findByIdAndUserIdAndDeletedFalse(branchId, userId)
                .map(Branch::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Şube bulunamadı"));
    }

    private StoreDtos.BusinessInfo suggestBusinessInfo(List<Branch> branches, List<Menu> menus) {
        Menu menu = menus.isEmpty() ? null : menus.getFirst();
        Branch branch = branches.isEmpty() ? null : branches.getFirst();
        if (menu == null && branch == null) {
            return null;
        }
        return StoreDtos.BusinessInfo.builder()
                .businessName(menu != null ? menu.getBusinessName() : branch.getName())
                .phone(firstPresent(menu == null ? null : menu.getPhone(), branch == null ? null : branch.getPhone()))
                .email(firstPresent(menu == null ? null : menu.getEmail(), branch == null ? null : branch.getEmail()))
                .address(firstPresent(menu == null ? null : menu.getAddress(), branch == null ? null : branch.getAddress()))
                .logoUrl(firstPresent(menu == null ? null : menu.getLogoUrl(), branch == null ? null : branch.getPhotoUrl()))
                .build();
    }

    private StoreDtos.BranchOption toBranchOption(Branch branch) {
        return StoreDtos.BranchOption.builder()
                .id(branch.getId())
                .name(branch.getName())
                .address(branch.getAddress())
                .phone(branch.getPhone())
                .email(branch.getEmail())
                .photoUrl(branch.getPhotoUrl())
                .build();
    }

    private List<Menu> cloneableMenus(Long userId) {
        return menuRepository.findActiveMenusWithQrByUserId(userId).stream()
                .map(row -> (Menu) row[0])
                .filter(menu -> menu.getChannel() != MenuChannel.STORE)
                .toList();
    }

    private StoreDtos.MenuOption toMenuOption(Menu menu, Map<Long, String> branchNames) {
        return StoreDtos.MenuOption.builder()
                .menuId(menu.getMenuId())
                .businessName(menu.getBusinessName())
                .productCount((int) menuProductRepository.countByMenuIdAndDeletedFalse(menu.getMenuId()))
                .branchName(menu.getBranchId() == null ? null : branchNames.get(menu.getBranchId()))
                .build();
    }

    private String firstPresent(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback == null || fallback.isBlank() ? null : fallback;
    }
}
