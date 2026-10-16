package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.RestaurantArea;
import com.ael.algoryqrservice.model.RestaurantLayout;
import com.ael.algoryqrservice.model.RestaurantTable;
import com.ael.algoryqrservice.model.enums.MenuChannel;
import com.ael.algoryqrservice.model.MerchantStaff;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.MerchantStaffRepository;
import com.ael.algoryqrservice.repository.RestaurantAreaRepository;
import com.ael.algoryqrservice.repository.RestaurantLayoutRepository;
import com.ael.algoryqrservice.repository.RestaurantTableRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class WaiterAccessService {

    private final MerchantStaffRepository merchantStaffRepository;
    private final MenuRepository menuRepository;
    private final RestaurantLayoutRepository restaurantLayoutRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final RestaurantAreaRepository restaurantAreaRepository;
    private final SecurityUtils securityUtils;

    public MerchantStaff requireCurrentWaiter() {
        Long staffId = securityUtils.getCurrentWaiterId();
        MerchantStaff waiter = merchantStaffRepository.findById(staffId)
                .orElseThrow(() -> new NotFoundException("Garson bulunamadı"));
        if (!waiter.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hesap pasif");
        }
        if (waiter.getBranchId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu hesap henüz bir şubeye atanmadı");
        }
        Long tokenBranchId = securityUtils.getCurrentWaiterBranchId();
        if (tokenBranchId == null || !tokenBranchId.equals(waiter.getBranchId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu şubeye erişim yetkiniz yok");
        }
        return waiter;
    }

    public MerchantStaff requireWaiterStaff() {
        MerchantStaff staff = requireCurrentWaiter();
        if (!staff.isWaiterStaff()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu işlem yalnızca garson içindir");
        }
        return staff;
    }

    public MerchantStaff requireKitchenStaff() {
        MerchantStaff staff = requireCurrentWaiter();
        if (!staff.isKitchen()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu işlem yalnızca mutfak içindir");
        }
        return staff;
    }

    public MerchantStaff requireCourierStaff() {
        MerchantStaff staff = requireCurrentWaiter();
        if (!staff.isCourier()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu işlem yalnızca kurye içindir");
        }
        return staff;
    }

    public MerchantStaff requireWaiterForMenu(Long menuId) {
        MerchantStaff waiter = requireWaiterStaff();
        requireMenuInWaiterBranch(menuId, waiter);
        return waiter;
    }

    public Menu requireMenuInWaiterBranch(Long menuId, MerchantStaff waiter) {
        if (menuId == null) {
            throw new NotFoundException("Menü bulunamadı");
        }
        Menu menu = menuRepository.findById(menuId)
                .filter(item -> !item.isDeleted())
                .orElseThrow(() -> new NotFoundException("Menü bulunamadı"));
        if (waiter.getBranchId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu hesap henüz bir şubeye atanmadı");
        }
        if (!waiter.getBranchId().equals(menu.getBranchId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu şubeye erişim yetkiniz yok");
        }
        return menu;
    }

    public List<Menu> menusForWaiter(MerchantStaff waiter) {
        if (waiter.getBranchId() == null) {
            return List.of();
        }
        return menuRepository.findByBranchIdAndChannelAndDeletedFalse(waiter.getBranchId(), MenuChannel.QR);
    }

    public List<Long> menuIdsForWaiter(MerchantStaff waiter) {
        return menusForWaiter(waiter).stream().map(Menu::getMenuId).toList();
    }

    public List<RestaurantTable> tablesForWaiter(MerchantStaff waiter) {
        if (waiter.getBranchId() == null) {
            return List.of();
        }
        LinkedHashMap<Long, RestaurantTable> tables = new LinkedHashMap<>();
        for (RestaurantLayout layout : restaurantLayoutRepository.findByBranchId(waiter.getBranchId())) {
            for (RestaurantTable table : restaurantTableRepository
                    .findByLayoutIdAndDeletedFalseOrderByTableNumberAscNameAsc(layout.getId())) {
                tables.putIfAbsent(table.getId(), table);
            }
        }
        if (tables.isEmpty()) {
            List<Long> menuIds = menuIdsForWaiter(waiter);
            if (!menuIds.isEmpty()) {
                for (RestaurantTable table : restaurantTableRepository
                        .findByMenuIdInOrderByTableNumberAscNameAsc(menuIds)) {
                    if (!table.isDeleted()) {
                        tables.putIfAbsent(table.getId(), table);
                    }
                }
            }
        }
        return List.copyOf(tables.values());
    }

    public Map<Long, String> areaNamesForTables(Collection<RestaurantTable> tables) {
        List<Long> layoutIds = tables.stream()
                .map(RestaurantTable::getLayoutId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<Long> menuIds = tables.stream()
                .map(RestaurantTable::getMenuId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> names = new LinkedHashMap<>();
        if (!layoutIds.isEmpty()) {
            for (RestaurantArea area : restaurantAreaRepository.findByLayoutIdInOrderBySortOrderAscIdAsc(layoutIds)) {
                names.putIfAbsent(area.getId(), area.getName());
            }
        }
        if (!menuIds.isEmpty()) {
            for (RestaurantArea area : restaurantAreaRepository.findByMenuIdInOrderBySortOrderAscIdAsc(menuIds)) {
                names.putIfAbsent(area.getId(), area.getName());
            }
        }
        return names;
    }
}
