package com.ael.algoryqrservice.qrservice.orderflow.service;

import com.ael.algoryqrservice.qrservice.model.Branch;
import com.ael.algoryqrservice.qrservice.model.enums.OrderFlowChannel;
import com.ael.algoryqrservice.qrservice.model.enums.OrderFlowStepType;
import com.ael.algoryqrservice.qrservice.repository.BranchRepository;
import com.ael.algoryqrservice.qrservice.repository.OrderFlowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sipariş servislerinin akışa bakması için yalnızca repository kullanan sorgular; {@link OrderFlowService}'in
 * şube yönetimi bağımlılıklarını sipariş akışına taşımamak için ayrıdır.
 */
@Service
@RequiredArgsConstructor
public class OrderFlowLookup {

    private final BranchRepository branchRepository;
    private final OrderFlowRepository orderFlowRepository;

    /** Masadaki QR siparişi garson onayı bekleyecek mi; akışı olmayan şube eski davranışla onaysız çalışır. */
    @Transactional(readOnly = true)
    public boolean requiresTableApproval(Long branchId) {
        return hasStep(branchId, OrderFlowChannel.TABLE, OrderFlowStepType.ORDER_APPROVAL);
    }

    private boolean hasStep(Long branchId, OrderFlowChannel channel, OrderFlowStepType type) {
        if (branchId == null) {
            return false;
        }
        return branchRepository.findById(branchId)
                .map(Branch::getOrderFlowId)
                .flatMap(orderFlowRepository::findByIdAndDeletedFalse)
                .map(flow -> flow.getSteps().stream().anyMatch(step -> step.getChannel() == channel && step.getStep() == type))
                .orElse(false);
    }
}
