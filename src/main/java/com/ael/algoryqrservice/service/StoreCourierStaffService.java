package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.MerchantStaff;
import com.ael.algoryqrservice.model.enums.StaffRole;
import com.ael.algoryqrservice.repository.MerchantStaffRepository;
import com.ael.algoryqrservice.store.model.Merchant;
import com.ael.algoryqrservice.store.model.StoreDeliveryType;
import com.ael.algoryqrservice.store.model.StoreOrder;
import com.ael.algoryqrservice.store.model.StoreOrderStatus;
import com.ael.algoryqrservice.store.model.dto.StoreOrderDtos;
import com.ael.algoryqrservice.store.repository.StoreOrderRepository;
import com.ael.algoryqrservice.store.service.StoreOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Courier staff accounts (merchant_staff, role COURIER) that receive delivery orders from the kitchen. */
@Service
@RequiredArgsConstructor
public class StoreCourierStaffService {

    private static final Set<StoreOrderStatus> ON_THE_ROAD = Set.of(
            StoreOrderStatus.COURIER_TAKEN,
            StoreOrderStatus.ON_THE_WAY
    );
    private static final Set<StoreOrderStatus> ASSIGNED = Set.of(
            StoreOrderStatus.KITCHEN_DELIVERED_TO_COURIER,
            StoreOrderStatus.WAITER_DELIVERED_TO_COURIER,
            StoreOrderStatus.COURIER_TAKEN,
            StoreOrderStatus.ON_THE_WAY
    );

    private final MerchantStaffRepository merchantStaffRepository;
    private final StoreOrderRepository storeOrderRepository;
    private final StoreOrderService storeOrderService;

    @Transactional(readOnly = true)
    public List<StoreOrderDtos.KitchenCourierOption> listKitchenCouriers(Merchant merchant, Long branchId) {
        List<MerchantStaff> couriers = merchantStaffRepository
                .findByMerchantIdAndStaffRoleAndActiveTrueOrderByDisplayNameAsc(merchant.getUserId(), StaffRole.COURIER)
                .stream()
                .filter(staff -> branchId == null || branchId.equals(staff.getBranchId()))
                .toList();
        if (couriers.isEmpty()) {
            return List.of();
        }
        Map<Long, List<StoreOrder>> assigned = storeOrderRepository
                .findByMerchantIdAndCourierStaffIdInAndStatusIn(
                        merchant.getId(),
                        couriers.stream().map(MerchantStaff::getId).toList(),
                        ASSIGNED
                )
                .stream()
                .collect(Collectors.groupingBy(StoreOrder::getCourierStaffId));
        return couriers.stream()
                .map(staff -> {
                    List<StoreOrder> orders = assigned.getOrDefault(staff.getId(), List.of());
                    int onTheRoad = (int) orders.stream().filter(order -> ON_THE_ROAD.contains(order.getStatus())).count();
                    return StoreOrderDtos.KitchenCourierOption.builder()
                            .id(staff.getId())
                            .displayName(staff.getDisplayName())
                            .branchId(staff.getBranchId())
                            .available(onTheRoad == 0)
                            .waitingCount(orders.size() - onTheRoad)
                            .onTheRoadCount(onTheRoad)
                            .build();
                })
                .toList();
    }

    @Transactional
    public StoreOrder handoverToCourier(Merchant merchant, Long orderId, Long courierStaffId, Long actorUserId) {
        StoreOrder order = storeOrderService.requireOrder(merchant.getId(), orderId);
        if (order.getDeliveryType() != StoreDeliveryType.DELIVERY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Adrese teslim olmayan sipariş kuryeye verilemez");
        }
        MerchantStaff courier = requireAvailableCourier(merchant, courierStaffId);
        if (!BranchScope.serves(order.getBranchId(), courier.getBranchId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Kurye bu siparişin şubesinde değil");
        }
        StoreOrder saved = storeOrderService.advance(
                merchant.getId(),
                orderId,
                StoreOrderStatus.KITCHEN_DELIVERED_TO_COURIER,
                actorUserId,
                null
        );
        saved.setCourierStaffId(courier.getId());
        saved.setAssignedAt(LocalDateTime.now());
        return storeOrderRepository.save(saved);
    }

    @Transactional(readOnly = true)
    public Optional<MerchantStaff> findCourierStaff(StoreOrder order) {
        return order.getCourierStaffId() == null
                ? Optional.empty()
                : merchantStaffRepository.findById(order.getCourierStaffId());
    }

    private MerchantStaff requireAvailableCourier(Merchant merchant, Long courierStaffId) {
        MerchantStaff courier = merchantStaffRepository.findById(courierStaffId)
                .filter(staff -> merchant.getUserId().equals(staff.getMerchantId()))
                .filter(MerchantStaff::isCourier)
                .filter(MerchantStaff::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kurye bulunamadı"));
        boolean onTheRoad = !storeOrderRepository
                .findByMerchantIdAndCourierStaffIdInAndStatusIn(merchant.getId(), List.of(courier.getId()), ON_THE_ROAD)
                .isEmpty();
        if (onTheRoad) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, courier.getDisplayName() + " şu anda teslimatta");
        }
        return courier;
    }
}
