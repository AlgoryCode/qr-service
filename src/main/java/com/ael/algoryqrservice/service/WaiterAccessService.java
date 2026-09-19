package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.enums.MenuChannel;
import com.ael.algoryqrservice.model.MenuWaiter;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.MenuWaiterRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WaiterAccessService {

    private final MenuWaiterRepository menuWaiterRepository;
    private final MenuRepository menuRepository;
    private final SecurityUtils securityUtils;

    public MenuWaiter requireCurrentWaiter() {
        Long waiterId = securityUtils.getCurrentWaiterId();
        MenuWaiter waiter = menuWaiterRepository.findById(waiterId)
                .orElseThrow(() -> new NotFoundException("Garson bulunamadı"));
        if (!waiter.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hesap pasif");
        }
        Long tokenBranchId = securityUtils.getCurrentWaiterBranchId();
        if (waiter.getBranchId() == null || !tokenBranchId.equals(waiter.getBranchId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu şubeye erişim yetkiniz yok");
        }
        return waiter;
    }

    public MenuWaiter requireWaiterStaff() {
        MenuWaiter staff = requireCurrentWaiter();
        if (!staff.isWaiterStaff()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu işlem yalnızca garson içindir");
        }
        return staff;
    }

    public MenuWaiter requireKitchenStaff() {
        MenuWaiter staff = requireCurrentWaiter();
        if (!staff.isKitchen()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu işlem yalnızca mutfak içindir");
        }
        return staff;
    }

    public MenuWaiter requireWaiterForMenu(Long menuId) {
        MenuWaiter waiter = requireWaiterStaff();
        requireMenuInWaiterBranch(menuId, waiter);
        return waiter;
    }

    public Menu requireMenuInWaiterBranch(Long menuId, MenuWaiter waiter) {
        if (menuId == null) {
            throw new NotFoundException("Menü bulunamadı");
        }
        Menu menu = menuRepository.findById(menuId)
                .filter(item -> !item.isDeleted())
                .orElseThrow(() -> new NotFoundException("Menü bulunamadı"));
        if (waiter.getBranchId() == null || !waiter.getBranchId().equals(menu.getBranchId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu şubeye erişim yetkiniz yok");
        }
        return menu;
    }

    public List<Menu> menusForWaiter(MenuWaiter waiter) {
        if (waiter.getBranchId() == null) {
            return List.of();
        }
        return menuRepository.findByBranchIdAndChannelAndDeletedFalse(waiter.getBranchId(), MenuChannel.QR);
    }

    public List<Long> menuIdsForWaiter(MenuWaiter waiter) {
        return menusForWaiter(waiter).stream().map(Menu::getMenuId).toList();
    }
}
