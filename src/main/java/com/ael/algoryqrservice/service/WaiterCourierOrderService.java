package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.model.MerchantStaff;
import com.ael.algoryqrservice.store.model.Merchant;
import com.ael.algoryqrservice.store.model.StoreCourier;
import com.ael.algoryqrservice.store.model.StoreDeliveryType;
import com.ael.algoryqrservice.store.model.StoreOrder;
import com.ael.algoryqrservice.store.model.StoreOrderStatus;
import com.ael.algoryqrservice.store.model.dto.StoreOrderDtos;
import com.ael.algoryqrservice.store.repository.MerchantRepository;
import com.ael.algoryqrservice.store.repository.StoreCourierRepository;
import com.ael.algoryqrservice.store.repository.StoreOrderRepository;
import com.ael.algoryqrservice.store.repository.StoreOrderSpecifications;
import com.ael.algoryqrservice.store.service.StoreOrderMapper;
import com.ael.algoryqrservice.store.service.StoreOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WaiterCourierOrderService {

    private static final int QUEUE_SIZE = 100;
    private static final List<StoreOrderStatus> QUEUE_STATUSES = List.of(
            StoreOrderStatus.PREPARING,
            StoreOrderStatus.READY,
            StoreOrderStatus.ON_THE_WAY,
            StoreOrderStatus.DELIVERED
    );

    private final WaiterAccessService waiterAccessService;
    private final MerchantRepository merchantRepository;
    private final StoreOrderRepository storeOrderRepository;
    private final StoreCourierRepository storeCourierRepository;
    private final StoreOrderService storeOrderService;
    private final StoreOrderMapper storeOrderMapper;

    @Transactional(readOnly = true)
    public List<StoreOrderDtos.OrderDetail> listQueue() {
        Merchant merchant = findStore(waiterAccessService.requireCourierStaff());
        if (merchant == null) {
            return List.of();
        }
        Map<Long, StoreCourier> couriers = loadCouriers(merchant.getId());
        return storeOrderRepository.findAll(
                        StoreOrderSpecifications.search(merchant.getId(), QUEUE_STATUSES, null, null)
                                .and((root, query, cb) -> cb.equal(root.get("deliveryType"), StoreDeliveryType.DELIVERY)),
                        PageRequest.of(0, QUEUE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"))
                )
                .getContent()
                .stream()
                .map(order -> storeOrderMapper.toDetail(order, couriers.get(order.getCourierId()), List.of()))
                .toList();
    }

    @Transactional
    public StoreOrderDtos.OrderDetail dispatch(Long orderId) {
        return advance(orderId, StoreOrderStatus.ON_THE_WAY);
    }

    @Transactional
    public StoreOrderDtos.OrderDetail deliver(Long orderId) {
        return advance(orderId, StoreOrderStatus.DELIVERED);
    }

    private StoreOrderDtos.OrderDetail advance(Long orderId, StoreOrderStatus target) {
        MerchantStaff staff = waiterAccessService.requireCourierStaff();
        Merchant merchant = requireStore(staff);
        StoreOrder existing = storeOrderService.requireOrder(merchant.getId(), orderId);
        if (existing.getDeliveryType() != StoreDeliveryType.DELIVERY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu sipariş adrese teslim değil");
        }
        StoreOrder saved = storeOrderService.advance(merchant.getId(), orderId, target, staff.getId(), null);
        StoreCourier courier = saved.getCourierId() == null
                ? null
                : storeCourierRepository.findByIdAndMerchantIdAndDeletedFalse(saved.getCourierId(), merchant.getId())
                .orElse(null);
        return storeOrderMapper.toDetail(saved, courier, List.of());
    }

    private Merchant findStore(MerchantStaff staff) {
        if (staff.getMerchantId() == null) {
            return null;
        }
        return merchantRepository.findByUserIdAndDeletedFalse(staff.getMerchantId()).orElse(null);
    }

    private Merchant requireStore(MerchantStaff staff) {
        Merchant merchant = findStore(staff);
        if (merchant == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Mağaza kurulumu yapılmamış");
        }
        return merchant;
    }

    private Map<Long, StoreCourier> loadCouriers(Long merchantId) {
        Map<Long, StoreCourier> byId = new LinkedHashMap<>();
        storeCourierRepository.findByMerchantIdAndDeletedFalseOrderByFullNameAsc(merchantId)
                .forEach(courier -> byId.put(courier.getId(), courier));
        return byId;
    }
}
