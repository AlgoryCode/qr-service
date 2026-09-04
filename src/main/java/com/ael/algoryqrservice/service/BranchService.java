package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.Branch;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.Qr;
import com.ael.algoryqrservice.model.dto.BranchDtos;
import com.ael.algoryqrservice.model.dto.ProductImageDtos;
import com.ael.algoryqrservice.repository.BranchRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.QrRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;
    private final MenuRepository menuRepository;
    private final QrRepository qrRepository;
    private final MenuQrSoftDeleteService menuQrSoftDeleteService;
    private final BranchQuotaService branchQuotaService;
    private final ProductImageStorageService productImageStorageService;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public BranchDtos.ListResponse listMine() {
        Long userId = securityUtils.getCurrentUserId();
        List<Branch> branches = branchRepository.findByUserIdAndDeletedFalseOrderByIdDesc(userId);
        Map<Long, List<Menu>> menusByBranch = menuRepository.findByUserIdAndDeletedFalseOrderByMenuIdAsc(userId)
                .stream()
                .filter(menu -> menu.getBranchId() != null)
                .collect(Collectors.groupingBy(Menu::getBranchId));
        List<BranchDtos.Response> content = branches.stream()
                .map(branch -> toResponse(branch, menusByBranch.getOrDefault(branch.getId(), List.of())))
                .toList();
        return BranchDtos.ListResponse.builder()
                .content(content)
                .quota(branchQuotaService.branchQuota(userId))
                .menuQuota(branchQuotaService.menuQuota(userId))
                .build();
    }

    @Transactional(readOnly = true)
    public BranchDtos.Response getMine(Long branchId) {
        Branch branch = requireOwned(branchId);
        return toResponse(branch, menuRepository.findByBranchIdAndDeletedFalse(branch.getId()));
    }

    @Transactional
    public BranchDtos.Response create(BranchDtos.CreateRequest request) {
        Long userId = securityUtils.getCurrentUserId();
        branchQuotaService.assertCanCreateBranch(userId);
        Branch branch = branchRepository.save(Branch.builder()
                .userId(userId)
                .name(request.getName().trim())
                .address(trimToNull(request.getAddress()))
                .phone(trimToNull(request.getPhone()))
                .email(trimToNull(request.getEmail()))
                .grandfathered(false)
                .active(true)
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
        return toResponse(branchRepository.save(branch), menuRepository.findByBranchIdAndDeletedFalse(branch.getId()));
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
        return toResponse(branch, menuRepository.findByBranchIdAndDeletedFalse(branch.getId()));
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
        return toResponse(branch, menuRepository.findByBranchIdAndDeletedFalse(branch.getId()));
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
        for (Menu menu : menuRepository.findByUserIdAndDeletedFalseOrderByMenuIdAsc(source.getUserId())) {
            menu.setLogoUrl(source.getPhotoUrl());
            menu.setLogoKey(source.getPhotoKey());
            menuRepository.save(menu);
        }
        return listMine();
    }

    @Transactional
    public void delete(Long branchId) {
        Branch branch = requireOwned(branchId);
        for (Menu menu : menuRepository.findByBranchIdAndDeletedFalse(branch.getId())) {
            Qr qr = qrRepository.findById(menu.getQrId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "QR bulunamadı"));
            menuQrSoftDeleteService.softDeleteMenuQr(qr);
        }
        branch.setDeleted(true);
        branchRepository.save(branch);
    }

    @Transactional
    public Branch requireOwned(Long branchId) {
        Long userId = securityUtils.getCurrentUserId();
        return branchRepository.findByIdAndUserIdAndDeletedFalse(branchId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Şube bulunamadı"));
    }

    @Transactional(readOnly = true)
    public Branch requireOwnedForUser(Long branchId, Long userId) {
        return branchRepository.findByIdAndUserIdAndDeletedFalse(branchId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçerli bir şube seçin"));
    }

    @Transactional
    public int backfillMissingBranches() {
        List<Menu> menus = menuRepository.findAll().stream()
                .filter(menu -> !menu.isDeleted())
                .filter(menu -> menu.getBranchId() == null)
                .toList();
        int created = 0;
        for (Menu menu : menus) {
            String name = menu.getBusinessName();
            if (name == null || name.isBlank()) {
                name = "Şube";
            }
            Branch branch = branchRepository.save(Branch.builder()
                    .userId(menu.getUserId())
                    .name(name.trim())
                    .address(trimToNull(menu.getAddress()))
                    .phone(trimToNull(menu.getPhone()))
                    .email(trimToNull(menu.getEmail()))
                    .photoUrl(trimToNull(menu.getLogoUrl()))
                    .photoKey(trimToNull(menu.getLogoKey()))
                    .grandfathered(true)
                    .active(menu.isActive())
                    .build());
            menu.setBranchId(branch.getId());
            menuRepository.save(menu);
            created++;
        }
        return created;
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
                .menus(menus.stream()
                        .map(menu -> BranchDtos.MenuSummary.builder()
                                .menuId(menu.getMenuId())
                                .qrId(menu.getQrId())
                                .businessName(menu.getBusinessName())
                                .active(menu.isActive())
                                .build())
                        .toList())
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
