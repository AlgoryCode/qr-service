package com.ael.algoryqrservice.staffservice.application;

import com.ael.algoryqrservice.qrservice.exception.ForbiddenException;
import com.ael.algoryqrservice.qrservice.exception.NotFoundException;
import com.ael.algoryqrservice.qrservice.model.Menu;
import com.ael.algoryqrservice.qrservice.model.RestaurantArea;
import com.ael.algoryqrservice.qrservice.model.RestaurantLayout;
import com.ael.algoryqrservice.qrservice.model.RestaurantTable;
import com.ael.algoryqrservice.qrservice.model.enums.MenuChannel;
import com.ael.algoryqrservice.staffservice.infrastructure.persistence.entity.MerchantStaff;
import com.ael.algoryqrservice.qrservice.repository.MenuRepository;
import com.ael.algoryqrservice.staffservice.infrastructure.persistence.repository.MerchantStaffRepository;
import com.ael.algoryqrservice.qrservice.repository.RestaurantAreaRepository;
import com.ael.algoryqrservice.qrservice.repository.RestaurantLayoutRepository;
import com.ael.algoryqrservice.qrservice.repository.RestaurantTableRepository;
import com.ael.algoryqrservice.qrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class StaffAccessService {

    private final MerchantStaffRepository merchantStaffRepository;
    private final MenuRepository menuRepository;
    private final RestaurantLayoutRepository restaurantLayoutRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final RestaurantAreaRepository restaurantAreaRepository;
    private final SecurityUtils securityUtils;

    /** JWT staffId + aktif kayıt; rol/şube token eşleşmesi kontrol edilmez. */
    public MerchantStaff requireCurrentStaff() {
        Long staffId = securityUtils.getCurrentStaffId();
        MerchantStaff staff = merchantStaffRepository.findById(staffId)
                .orElseThrow(() -> new NotFoundException("Personel bulunamadı"));
        if (!staff.isActive()) {
            throw new ForbiddenException("Hesap pasif");
        }
        return staff;
    }

    public MerchantStaff requireCurrentWaiter() {
        return requireCurrentStaff();
    }

    public MerchantStaff requireWaiterStaff() {
        return requireCurrentStaff();
    }

    public MerchantStaff requireWaiterStaffForMerchantBranch(Long merchantId, Long branchId) {
        MerchantStaff staff = requireCurrentStaff();
        if (merchantId != null && !merchantId.equals(staff.getMerchantId())) {
            throw new ForbiddenException("Bu işletmeye erişim yetkiniz yok");
        }
        if (branchId != null && staff.getBranchId() != null && !branchId.equals(staff.getBranchId())) {
            throw new ForbiddenException("Bu şubeye erişim yetkiniz yok");
        }
        return staff;
    }

    public MerchantStaff requireKitchenStaff() {
        return requireCurrentStaff();
    }

    public MerchantStaff requireCourierStaff() {
        return requireCurrentStaff();
    }

    public MerchantStaff requireWaiterForMenu(Long menuId) {
        MerchantStaff staff = requireCurrentStaff();
        requireMenuInWaiterBranch(menuId, staff);
        return staff;
    }

    public Menu requireMenuInWaiterBranch(Long menuId, MerchantStaff waiter) {
        if (menuId == null) {
            throw new NotFoundException("Menü bulunamadı");
        }
        Menu menu = menuRepository.findById(menuId)
                .filter(item -> !item.isDeleted())
                .orElseThrow(() -> new NotFoundException("Menü bulunamadı"));
        if (waiter.getBranchId() == null) {
            throw new ForbiddenException("Bu hesap henüz bir şubeye atanmadı");
        }
        if (!waiter.getBranchId().equals(menu.getBranchId())) {
            throw new ForbiddenException("Bu şubeye erişim yetkiniz yok");
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
