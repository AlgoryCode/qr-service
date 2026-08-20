package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.MenuWaiter;
import com.ael.algoryqrservice.model.dto.TableBillDtos;
import com.ael.algoryqrservice.repository.MenuWaiterRepository;
import com.ael.algoryqrservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MenuWaiterBillService {

    private final TableBillService tableBillService;
    private final MenuWaiterRepository menuWaiterRepository;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public TableBillDtos.BillResponse getOpenBillForTable(Long menuId, Long tableId) {
        requireWaiterForMenu(menuId);
        return tableBillService.getOpenBillForTable(menuId, tableId);
    }

    @Transactional(readOnly = true)
    public TableBillDtos.BillResponse getBill(Long menuId, Long billId) {
        requireWaiterForMenu(menuId);
        return tableBillService.getBill(menuId, billId);
    }

    @Transactional
    public TableBillDtos.BillResponse addItems(Long billId, TableBillDtos.UpdateBillItemsRequest request) {
        MenuWaiter waiter = requireCurrentWaiter();
        return tableBillService.addItems(
                waiter.getMenuId(),
                billId,
                request.getItems(),
                waiter.getId()
        );
    }

    @Transactional
    public TableBillDtos.BillResponse updateItemQuantity(Long billId, Long itemId, int quantity) {
        MenuWaiter waiter = requireCurrentWaiter();
        return tableBillService.updateItemQuantity(waiter.getMenuId(), billId, itemId, quantity);
    }

    @Transactional
    public TableBillDtos.BillResponse removeItem(Long billId, Long itemId) {
        MenuWaiter waiter = requireCurrentWaiter();
        return tableBillService.removeItem(waiter.getMenuId(), billId, itemId);
    }

    @Transactional
    public TableBillDtos.BillResponse payItems(Long billId, TableBillDtos.PayBillItemsRequest request) {
        MenuWaiter waiter = requireCurrentWaiter();
        return tableBillService.payItems(waiter.getMenuId(), billId, waiter, request);
    }

    @Transactional
    public TableBillDtos.BillResponse payShare(Long billId, TableBillDtos.PayBillShareRequest request) {
        MenuWaiter waiter = requireCurrentWaiter();
        return tableBillService.payShare(waiter.getMenuId(), billId, waiter, request);
    }

    @Transactional(readOnly = true)
    public TableBillDtos.SplitPreviewResponse getSplitPreview(Long billId, int personCount) {
        MenuWaiter waiter = requireCurrentWaiter();
        return tableBillService.getSplitPreview(waiter.getMenuId(), billId, personCount);
    }

    @Transactional
    public TableBillDtos.BillResponse closeBill(Long billId, TableBillDtos.CloseBillRequest request) {
        MenuWaiter waiter = requireCurrentWaiter();
        return tableBillService.closeBill(
                waiter.getMenuId(),
                billId,
                waiter,
                request != null ? request.getPaymentMethod() : null,
                request != null ? request.getTipReceived() : null,
                request != null ? request.getTipAmount() : null
        );
    }

    private MenuWaiter requireCurrentWaiter() {
        Long waiterId = securityUtils.getCurrentWaiterId();
        MenuWaiter waiter = menuWaiterRepository.findById(waiterId)
                .orElseThrow(() -> new NotFoundException("Garson bulunamadı"));
        if (!waiter.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Garson hesabı pasif");
        }
        return waiter;
    }

    private void requireWaiterForMenu(Long menuId) {
        MenuWaiter waiter = requireCurrentWaiter();
        if (!menuId.equals(waiter.getMenuId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu menüye erişim yetkiniz yok");
        }
    }
}
