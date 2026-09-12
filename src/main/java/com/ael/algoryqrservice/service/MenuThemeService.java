package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.catalog.CatalogThemes;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.ForbiddenException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.MenuTheme;
import com.ael.algoryqrservice.model.UserThemeAssignment;
import com.ael.algoryqrservice.model.dto.ThemeDtos;
import com.ael.algoryqrservice.repository.MenuThemeRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.repository.UserThemeAssignmentRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuThemeService {

    private final MenuThemeRepository menuThemeRepository;
    private final UserThemeAssignmentRepository userThemeAssignmentRepository;
    private final UserRepository userRepository;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public List<ThemeDtos.ThemeResponse> listAllowedThemesForCurrentUser() {
        Long userId = securityUtils.getCurrentUserId();
        return listAssignedThemes(userId);
    }

    @Transactional(readOnly = true)
    public List<ThemeDtos.ThemeResponse> listAssignedThemes(Long userId) {
        requireExistingUser(userId);
        List<UserThemeAssignment> assignments = userThemeAssignmentRepository.findByUserId(userId);
        if (assignments.isEmpty()) {
            return List.of();
        }
        Set<Long> themeIds = assignments.stream()
                .map(UserThemeAssignment::getThemeId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return menuThemeRepository.findByIdInAndActiveTrueOrderBySortOrderAscIdAsc(themeIds).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ThemeDtos.ThemeResponse> listCatalog(boolean includeInactive) {
        List<MenuTheme> themes = includeInactive
                ? menuThemeRepository.findAllByOrderBySortOrderAscIdAsc()
                : menuThemeRepository.findAllByActiveTrueOrderBySortOrderAscIdAsc();
        return themes.stream().map(this::toResponse).toList();
    }

    /**
     * Rejects menu theme updates when the preset theme is missing from the catalog
     * or not assigned to the member. Custom themes ({@code custom-*}) are handled by
     * {@link MenuService} via {@code CUSTOM_DESIGN_OWNER}.
     */
    @Transactional(readOnly = true)
    public void requireAssignedTheme(Long userId, String themeId) {
        String code = normalizeCode(themeId);
        if (code == null) {
            throw new BadRequestException("themeId zorunludur");
        }
        if (CatalogThemes.isCustomTheme(code)) {
            return;
        }
        MenuTheme theme = menuThemeRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new BadRequestException("Geçersiz tema: " + code));
        if (!theme.isActive()) {
            throw new BadRequestException("Bu tema kullanılamıyor: " + code);
        }
        if (!userThemeAssignmentRepository.existsByUserIdAndThemeId(userId, theme.getId())) {
            throw new ForbiddenException("Bu temayı kullanma yetkiniz yok");
        }
    }

    @Transactional
    public List<ThemeDtos.ThemeResponse> assignThemes(Long userId, List<String> themeCodes, Long assignedBy) {
        requireExistingUser(userId);
        List<MenuTheme> themes = resolveActiveThemes(themeCodes);
        for (MenuTheme theme : themes) {
            if (!userThemeAssignmentRepository.existsByUserIdAndThemeId(userId, theme.getId())) {
                userThemeAssignmentRepository.save(UserThemeAssignment.builder()
                        .userId(userId)
                        .themeId(theme.getId())
                        .assignedBy(assignedBy)
                        .build());
            }
        }
        return listAssignedThemes(userId);
    }

    @Transactional
    public List<ThemeDtos.ThemeResponse> replaceThemes(Long userId, List<String> themeCodes, Long assignedBy) {
        requireExistingUser(userId);
        List<String> codes = themeCodes == null ? List.of() : themeCodes;
        List<MenuTheme> themes = codes.isEmpty() ? List.of() : resolveActiveThemes(codes);
        userThemeAssignmentRepository.deleteByUserId(userId);
        userThemeAssignmentRepository.flush();
        for (MenuTheme theme : themes) {
            userThemeAssignmentRepository.save(UserThemeAssignment.builder()
                    .userId(userId)
                    .themeId(theme.getId())
                    .assignedBy(assignedBy)
                    .build());
        }
        return listAssignedThemes(userId);
    }

    @Transactional
    public List<ThemeDtos.ThemeResponse> revokeTheme(Long userId, String themeCode) {
        requireExistingUser(userId);
        String code = normalizeCode(themeCode);
        if (code == null) {
            throw new BadRequestException("Tema kodu zorunludur");
        }
        MenuTheme theme = menuThemeRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new NotFoundException("Tema bulunamadı: " + code));
        userThemeAssignmentRepository.deleteByUserIdAndThemeId(userId, theme.getId());
        return listAssignedThemes(userId);
    }

    private List<MenuTheme> resolveActiveThemes(List<String> themeCodes) {
        if (themeCodes == null || themeCodes.isEmpty()) {
            throw new BadRequestException("En az bir tema kodu gerekli");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String raw : themeCodes) {
            String code = normalizeCode(raw);
            if (code == null) {
                throw new BadRequestException("Tema kodu boş olamaz");
            }
            if (CatalogThemes.isCustomTheme(code)) {
                throw new BadRequestException("Özel temalar (custom-*) katalog atamasına eklenemez");
            }
            normalized.add(code);
        }

        Map<String, MenuTheme> byCode = new LinkedHashMap<>();
        for (MenuTheme theme : menuThemeRepository.findByCodeIn(normalized)) {
            byCode.put(theme.getCode().toLowerCase(Locale.ROOT), theme);
        }

        List<MenuTheme> resolved = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String code : normalized) {
            MenuTheme theme = byCode.get(code.toLowerCase(Locale.ROOT));
            if (theme == null || !theme.isActive()) {
                missing.add(code);
            } else {
                resolved.add(theme);
            }
        }
        if (!missing.isEmpty()) {
            throw new BadRequestException("Geçersiz veya pasif tema kodları: " + String.join(", ", missing));
        }
        return resolved;
    }

    private void requireExistingUser(Long userId) {
        if (userId == null || !userRepository.existsById(userId)) {
            throw new NotFoundException("Kullanıcı bulunamadı");
        }
    }

    private ThemeDtos.ThemeResponse toResponse(MenuTheme theme) {
        return ThemeDtos.ThemeResponse.builder()
                .id(theme.getId())
                .code(theme.getCode())
                .name(theme.getName())
                .description(theme.getDescription())
                .previewMeta(theme.getPreviewMeta())
                .sortOrder(theme.getSortOrder())
                .active(theme.isActive())
                .build();
    }

    private String normalizeCode(String themeId) {
        if (themeId == null || themeId.isBlank()) {
            return null;
        }
        return themeId.trim().toLowerCase(Locale.ROOT);
    }
}
