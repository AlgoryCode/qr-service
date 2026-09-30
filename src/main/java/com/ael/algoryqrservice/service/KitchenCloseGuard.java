package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.model.Branch;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.enums.MenuChannel;
import com.ael.algoryqrservice.model.enums.MenuOrderStatus;
import com.ael.algoryqrservice.repository.MenuOrderRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.store.model.StoreOrderStatus;
import com.ael.algoryqrservice.store.repository.MerchantRepository;
import com.ael.algoryqrservice.store.repository.StoreOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Şubenin mutfağında işi bitmemiş masa, online veya entegrasyon siparişi varsa mutfak kapatılamaz. */
@Service
@RequiredArgsConstructor
public class KitchenCloseGuard {

    static final String ACTIVE_ORDERS_MESSAGE = "Aktif olan siparişleriniz var, mutfağı kapatamazsınız";

    private static final Set<MenuOrderStatus> ACTIVE_TABLE_STATUSES = EnumSet.of(
            MenuOrderStatus.SUBMITTED,
            MenuOrderStatus.CONFIRMED,
            MenuOrderStatus.PREPARING,
            MenuOrderStatus.READY
    );

    private static final Set<StoreOrderStatus> ACTIVE_STORE_KITCHEN_STATUSES = EnumSet.of(
            StoreOrderStatus.PENDING,
            StoreOrderStatus.CONFIRMED,
            StoreOrderStatus.PREPARING,
            StoreOrderStatus.READY,
            StoreOrderStatus.KITCHEN_PREPARING,
            StoreOrderStatus.KITCHEN_PREPARED
    );

    private final MenuRepository menuRepository;
    private final MenuOrderRepository menuOrderRepository;
    private final MerchantRepository merchantRepository;
    private final StoreOrderRepository storeOrderRepository;
    private final KitchenUberEatsService kitchenUberEatsService;
    private final KitchenYemekSepetiService kitchenYemekSepetiService;

    public void requireNoActiveOrders(Branch branch) {
        if (hasActiveOrders(branch)) {
            throw new BadRequestException(ACTIVE_ORDERS_MESSAGE);
        }
    }

    private boolean hasActiveOrders(Branch branch) {
        List<Long> menuIds = menuRepository.findByBranchIdAndChannelAndDeletedFalse(branch.getId(), MenuChannel.QR)
                .stream()
                .map(Menu::getMenuId)
                .toList();
        if (!menuIds.isEmpty() && menuOrderRepository.existsByMenuIdInAndStatusIn(menuIds, ACTIVE_TABLE_STATUSES)) {
            return true;
        }
        Long ownerId = branch.getUserId();
        if (ownerId == null) {
            return false;
        }
        boolean storeActive = merchantRepository.findByUserIdAndDeletedFalse(ownerId)
                .map(merchant -> storeOrderRepository.existsForBranch(
                        merchant.getId(),
                        branch.getId(),
                        ACTIVE_STORE_KITCHEN_STATUSES
                ))
                .orElse(false);
        return storeActive
                || !kitchenUberEatsService.listActiveForBranch(ownerId, branch.getId()).isEmpty()
                || !kitchenYemekSepetiService.listActiveForBranch(ownerId, branch.getId()).isEmpty();
    }
}
