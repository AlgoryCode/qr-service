package com.ael.algoryqrservice.qrservice.branch.service;

import com.ael.algoryqrservice.qrservice.product.catalog.CatalogProducts;
import com.ael.algoryqrservice.qrservice.client.FulfillmentServiceClient;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.security.ProductUsageGateway;
import com.ael.algoryqrservice.qrservice.model.Branch;
import com.ael.algoryqrservice.qrservice.model.Menu;
import com.ael.algoryqrservice.qrservice.qr.model.Qr;
import com.ael.algoryqrservice.qrservice.branch.dto.BranchDtos;
import com.ael.algoryqrservice.qrservice.product.dto.ProductImageDtos;
import com.ael.algoryqrservice.qrservice.model.enums.MenuChannel;
import com.ael.algoryqrservice.qrservice.repository.BranchRepository;
import com.ael.algoryqrservice.qrservice.repository.MenuRepository;
import com.ael.algoryqrservice.qrservice.qr.repository.QrRepository;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.ael.algoryqrservice.qrservice.menu.service.MenuQrSoftDeleteService;
import com.ael.algoryqrservice.qrservice.product.service.ProductImageStorageService;
import com.ael.algoryqrservice.qrservice.order.service.KitchenCloseGuard;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;
    private final MenuRepository menuRepository;
    private final QrRepository qrRepository;
    private final MenuQrSoftDeleteService menuQrSoftDeleteService;
    private final FulfillmentServiceClient fulfillmentClient;
    private final ProductUsageGateway productUsageGateway;
    private final ProductImageStorageService productImageStorageService;
    private final SecurityUtils securityUtils;
    private final KitchenCloseGuard kitchenCloseGuard;

    @Transactional(readOnly = true)
    public BranchDtos.ListResponse listMine() {
        Long userId = securityUtils.getCurrentMerchantId();
        List<Branch> branches = branchRepository.findByUserIdAndDeletedFalseOrderByIdDesc(userId);
        Map<Long, List<Menu>> menusByBranch = menuRepository
                .findByUserIdAndChannelAndDeletedFalseOrderByMenuIdAsc(userId, MenuChannel.QR)
                .stream()
                .filter(menu -> menu.getBranchId() != null)
                .collect(Collectors.groupingBy(Menu::getBranchId));
        List<BranchDtos.Response> content = branches.stream()
                .map(branch -> toResponse(branch, menusByBranch.getOrDefault(branch.getId(), List.of())))
                .toList();
        return BranchDtos.ListResponse.builder()
                .content(content)
                .quota(branchQuota(userId))
                .menuQuota(menuQuota(userId))
                .build();
    }

    @Transactional(readOnly = true)
    public BranchDtos.Response getMine(Long branchId) {
        Branch branch = requireOwned(branchId);
        return toResponse(branch, menuRepository.findByBranchIdAndChannelAndDeletedFalse(branch.getId(), MenuChannel.QR));
    }

    @Transactional
    public BranchDtos.Response create(BranchDtos.CreateRequest request) {
        Long userId = securityUtils.getCurrentMerchantId();
        consumeBranch(userId);
        Branch branch = branchRepository.save(Branch.builder()
                .userId(userId)
                .name(request.getName().trim())
                .address(trimToNull(request.getAddress()))
                .phone(trimToNull(request.getPhone()))
                .email(trimToNull(request.getEmail()))
                .grandfathered(false)
                .active(true)
                .kitchenEnabled(false)
                .courierEnabled(false)
                .printKitchenEnabled(false)
                .build());
        return toResponse(branch, List.of());
    }

    @Transactional
    public BranchDtos.Response update(Long branchId, BranchDtos.UpdateRequest request) {
        Branch branch = requireOwned(branchId);
        if (request.getName() != null && !request.getName().isBlank()) {
            branch.setName(request.getName().trim());
        }
        if (request.getAddress() != null) {
            branch.setAddress(trimToNull(request.getAddress()));
        }
        if (request.getPhone() != null) {
            branch.setPhone(trimToNull(request.getPhone()));
        }
        if (request.getEmail() != null) {
            branch.setEmail(trimToNull(request.getEmail()));
        }
        if (request.getActive() != null) {
            branch.setActive(request.getActive());
        }
        if (request.getKitchenEnabled() != null) {
            if (branch.isKitchenEnabled() && !request.getKitchenEnabled()) {
                kitchenCloseGuard.requireNoActiveOrders(branch);
            }
            branch.setKitchenEnabled(request.getKitchenEnabled());
        }
        if (request.getCourierEnabled() != null) {
            branch.setCourierEnabled(request.getCourierEnabled());
        }
        if (request.getPrintKitchenEnabled() != null) {
            branch.setPrintKitchenEnabled(request.getPrintKitchenEnabled());
        }
        return toResponse(branchRepository.save(branch), menuRepository.findByBranchIdAndChannelAndDeletedFalse(branch.getId(), MenuChannel.QR));
    }

    @Transactional
    public BranchDtos.Response enableKitchen(Long branchId) {
        Branch branch = requireOwned(branchId);
        branch.setKitchenEnabled(true);
        return toResponse(
                branchRepository.save(branch),
                menuRepository.findByBranchIdAndChannelAndDeletedFalse(branch.getId(), MenuChannel.QR)
        );
    }

    @Transactional
    public BranchDtos.Response uploadPhoto(Long branchId, MultipartFile file) {
        Branch branch = requireOwned(branchId);
        ProductImageDtos.UploadResponse uploaded = productImageStorageService.uploadBranchPhoto(branchId, file);
        String previousKey = branch.getPhotoKey();
        branch.setPhotoUrl(uploaded.imageUrl());
        branch.setPhotoKey(uploaded.objectKey());
        branchRepository.save(branch);
        if (previousKey != null && !previousKey.isBlank() && !previousKey.equals(uploaded.objectKey())) {
            productImageStorageService.deleteQuietly(previousKey);
        }
        return toResponse(branch, menuRepository.findByBranchIdAndChannelAndDeletedFalse(branch.getId(), MenuChannel.QR));
    }

    @Transactional
    public BranchDtos.Response clearPhoto(Long branchId) {
        Branch branch = requireOwned(branchId);
        String previousKey = branch.getPhotoKey();
        branch.setPhotoUrl(null);
        branch.setPhotoKey(null);
        branchRepository.save(branch);
        if (previousKey != null && !previousKey.isBlank()) {
            productImageStorageService.deleteQuietly(previousKey);
        }
        return toResponse(branch, menuRepository.findByBranchIdAndChannelAndDeletedFalse(branch.getId(), MenuChannel.QR));
    }

    @Transactional
    public BranchDtos.ListResponse applyPhotoToAllBranches(Long branchId) {
        Branch source = requireOwned(branchId);
        if (source.getPhotoUrl() == null || source.getPhotoUrl().isBlank()) {
            throw new BadRequestException("Önce şube fotoğrafı yükleyin");
        }
        Long userId = source.getUserId();
        for (Branch branch : branchRepository.findByUserIdAndDeletedFalse(userId)) {
            if (branch.getId().equals(source.getId())) {
                continue;
            }
            branch.setPhotoUrl(source.getPhotoUrl());
            branch.setPhotoKey(source.getPhotoKey());
            branchRepository.save(branch);
        }
        return listMine();
    }

    @Transactional
    public BranchDtos.ListResponse applyPhotoToAllMenus(Long branchId) {
        Branch source = requireOwned(branchId);
        if (source.getPhotoUrl() == null || source.getPhotoUrl().isBlank()) {
            throw new BadRequestException("Önce şube fotoğrafı yükleyin");
        }
        for (Menu menu : menuRepository
                .findByUserIdAndChannelAndDeletedFalseOrderByMenuIdAsc(source.getUserId(), MenuChannel.QR)) {
            menu.setLogoUrl(source.getPhotoUrl());
            menu.setLogoKey(source.getPhotoKey());
            menuRepository.save(menu);
        }
        return listMine();
    }

    @Transactional
    public void delete(Long branchId) {
        Branch branch = requireOwned(branchId);
        for (Menu menu : menuRepository.findByBranchIdAndChannelAndDeletedFalse(branch.getId(), MenuChannel.QR)) {
            Qr qr = qrRepository.findById(menu.getQrId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "QR bulunamadı"));
            menuQrSoftDeleteService.softDeleteMenuQr(qr);
        }
        branch.setDeleted(true);
        branchRepository.save(branch);
    }

    @Transactional
    public Branch requireOwned(Long branchId) {
        Long userId = securityUtils.getCurrentMerchantId();
        return branchRepository.findByIdAndUserIdAndDeletedFalse(branchId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Şube bulunamadı"));
    }

    @Transactional(readOnly = true)
    public Branch requireOwnedForUser(Long branchId, Long userId) {
        return branchRepository.findByIdAndUserIdAndDeletedFalse(branchId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçerli bir şube seçin"));
    }

    private BranchDtos.Response toResponse(Branch branch, List<Menu> menus) {
        return BranchDtos.Response.builder()
                .id(branch.getId())
                .userId(branch.getUserId())
                .name(branch.getName())
                .address(branch.getAddress())
                .phone(branch.getPhone())
                .email(branch.getEmail())
                .photoUrl(branch.getPhotoUrl())
                .grandfathered(branch.isGrandfathered())
                .active(branch.isActive())
                .kitchenEnabled(branch.isKitchenEnabled())
                .courierEnabled(branch.isCourierEnabled())
                .printKitchenEnabled(branch.isPrintKitchenEnabled())
                .menus(menus.stream()
                        .map(menu -> BranchDtos.MenuSummary.builder()
                                .menuId(menu.getMenuId())
                                .qrId(menu.getQrId())
                                .publicId(menu.getPublicId())
                                .businessName(menu.getBusinessName())
                                .active(menu.isActive())
                                .build())
                        .toList())
                .build();
    }

    private void consumeBranch(Long userId) {
        productUsageGateway.use(userId, CatalogProducts.QR_BRANCH, 1);
    }

    private BranchDtos.Quota branchQuota(Long userId) {
        int used = (int) branchRepository.countByUserIdAndDeletedFalse(userId);
        int remaining = fulfillmentClient.remaining(userId, CatalogProducts.QR_BRANCH);
        return BranchDtos.Quota.builder()
                .used(used)
                .allowed(used + remaining)
                .remaining(remaining)
                .grandfathered(0)
                .extraPurchased(0)
                .canCreate(remaining > 0)
                .build();
    }

    private BranchDtos.MenuQuota menuQuota(Long userId) {
        int remaining = fulfillmentClient.remaining(userId, CatalogProducts.QR_MENU);
        return BranchDtos.MenuQuota.builder()
                .extraUsed(0)
                .extraAllowed(remaining)
                .extraRemaining(remaining)
                .canCreateExtra(remaining > 0)
                .build();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
