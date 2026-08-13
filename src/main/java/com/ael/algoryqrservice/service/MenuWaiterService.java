package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuWaiter;
import com.ael.algoryqrservice.model.User;
import com.ael.algoryqrservice.model.dto.MenuWaiterDtos;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.MenuWaiterRepository;
import com.ael.algoryqrservice.repository.UserRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MenuWaiterService {

    private final MenuWaiterRepository menuWaiterRepository;
    private final MenuRepository menuRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public MenuWaiterDtos.UsersPageResponse listWaiters(Long menuId) {
        Menu menu = requireOwnedMenu(menuId);
        MenuWaiterDtos.OwnerSummary owner = getOwnerSummary(menu);
        List<MenuWaiterDtos.WaiterResponse> waiters = menuWaiterRepository
                .findByMenuIdOrderByDisplayNameAsc(menu.getMenuId())
                .stream()
                .map(this::toWaiterResponse)
                .toList();
        return MenuWaiterDtos.UsersPageResponse.builder()
                .owner(owner)
                .waiters(waiters)
                .build();
    }

    @Transactional
    public MenuWaiterDtos.WaiterResponse createWaiter(Long menuId, MenuWaiterDtos.CreateWaiterRequest request) {
        Menu menu = requireOwnedMenu(menuId);
        if (request == null) {
            throw new BadRequestException("İstek gövdesi zorunludur");
        }

        String username = normalizeUsername(request.getUsername());
        if (menuWaiterRepository.existsByUsernameIgnoreCase(username)) {
            throw new BadRequestException("Bu kullanıcı adı zaten kullanılıyor");
        }

        String displayName = requireDisplayName(request.getDisplayName());
        LocalDateTime now = LocalDateTime.now();

        MenuWaiter waiter = MenuWaiter.builder()
                .ownerUserId(menu.getUserId())
                .menuId(menu.getMenuId())
                .username(username)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(displayName)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return toWaiterResponse(menuWaiterRepository.save(waiter));
    }

    @Transactional
    public MenuWaiterDtos.WaiterResponse updateWaiter(
            Long menuId,
            Long waiterId,
            MenuWaiterDtos.UpdateWaiterRequest request
    ) {
        requireOwnedMenu(menuId);
        MenuWaiter waiter = requireWaiter(menuId, waiterId);

        if (request != null) {
            if (request.getDisplayName() != null) {
                waiter.setDisplayName(requireDisplayName(request.getDisplayName()));
            }
            if (request.getActive() != null) {
                waiter.setActive(request.getActive());
            }
            if (request.getPassword() != null && !request.getPassword().isBlank()) {
                waiter.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            }
        }

        waiter.setUpdatedAt(LocalDateTime.now());
        return toWaiterResponse(menuWaiterRepository.save(waiter));
    }

    @Transactional
    public void deleteWaiter(Long menuId, Long waiterId) {
        requireOwnedMenu(menuId);
        MenuWaiter waiter = requireWaiter(menuId, waiterId);
        waiter.setActive(false);
        waiter.setUpdatedAt(LocalDateTime.now());
        menuWaiterRepository.save(waiter);
    }

    @Transactional(readOnly = true)
    public MenuWaiterDtos.OwnerSummary getOwnerSummary(Long menuId) {
        Menu menu = requireOwnedMenu(menuId);
        return getOwnerSummary(menu);
    }

    private MenuWaiterDtos.OwnerSummary getOwnerSummary(Menu menu) {
        User owner = userRepository.findById(menu.getUserId())
                .orElseThrow(() -> new NotFoundException("İşletme sahibi bulunamadı"));
        return MenuWaiterDtos.OwnerSummary.builder()
                .id(owner.getId())
                .firstName(owner.getFirstName())
                .lastName(owner.getLastName())
                .email(owner.getEmail())
                .build();
    }

    private Menu requireOwnedMenu(Long menuId) {
        Menu menu = menuRepository.findById(menuId)
                .filter(m -> !m.isDeleted())
                .orElseThrow(() -> new NotFoundException("Menü bulunamadı"));
        Long currentUserId = securityUtils.getCurrentUserId();
        if (!currentUserId.equals(menu.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu menüye erişim yetkiniz yok");
        }
        return menu;
    }

    private MenuWaiter requireWaiter(Long menuId, Long waiterId) {
        return menuWaiterRepository.findByIdAndMenuId(waiterId, menuId)
                .orElseThrow(() -> new NotFoundException("Garson bulunamadı"));
    }

    private MenuWaiterDtos.WaiterResponse toWaiterResponse(MenuWaiter waiter) {
        return MenuWaiterDtos.WaiterResponse.builder()
                .id(waiter.getId())
                .menuId(waiter.getMenuId())
                .username(waiter.getUsername())
                .displayName(waiter.getDisplayName())
                .active(waiter.isActive())
                .createdAt(waiter.getCreatedAt())
                .build();
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new BadRequestException("Kullanıcı adı zorunludur");
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private String requireDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new BadRequestException("Görünen ad zorunludur");
        }
        return displayName.trim();
    }
}
