package com.ael.algoryqrservice.service;

import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.BillPayment;
import com.ael.algoryqrservice.model.Menu;
import com.ael.algoryqrservice.model.MenuOrder;
import com.ael.algoryqrservice.model.MenuWaiter;
import com.ael.algoryqrservice.model.WorkShift;
import com.ael.algoryqrservice.model.dto.WorkShiftDtos;
import com.ael.algoryqrservice.model.enums.WorkShiftStatus;
import com.ael.algoryqrservice.repository.BillPaymentRepository;
import com.ael.algoryqrservice.repository.MenuOrderRepository;
import com.ael.algoryqrservice.repository.MenuRepository;
import com.ael.algoryqrservice.repository.WorkShiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WorkShiftService {

    private final WorkShiftRepository workShiftRepository;
    private final WaiterAccessService waiterAccessService;
    private final MenuRepository menuRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final MenuOrderRepository menuOrderRepository;

    @Transactional
    public WorkShiftDtos.ShiftResponse openShift(WorkShiftDtos.OpenShiftRequest request) {
        MenuWaiter waiter = waiterAccessService.requireCurrentWaiter();
        workShiftRepository.findFirstByBranchIdAndStatusOrderByOpenedAtDesc(waiter.getBranchId(), WorkShiftStatus.OPEN)
                .ifPresent(open -> {
                    throw new BadRequestException("Zaten açık bir vardiya var");
                });
        if (request.getMenuId() != null) {
            Menu menu = menuRepository.findById(request.getMenuId())
                    .orElseThrow(() -> new NotFoundException("Menü bulunamadı"));
            if (!waiter.getBranchId().equals(menu.getBranchId())) {
                throw new BadRequestException("Menü bu şubeye ait değil");
            }
        }
        Set<Long> waiterIds = request.getWaiterIds() == null
                ? new HashSet<>(Set.of(waiter.getId()))
                : new HashSet<>(request.getWaiterIds());
        waiterIds.add(waiter.getId());

        WorkShift shift = WorkShift.builder()
                .branchId(waiter.getBranchId())
                .menuId(request.getMenuId())
                .openedByWaiterId(waiter.getId())
                .openedAt(LocalDateTime.now())
                .openingFloat(request.getOpeningFloat() == null ? BigDecimal.ZERO : request.getOpeningFloat())
                .note(request.getNote())
                .status(WorkShiftStatus.OPEN)
                .waiterIds(waiterIds)
                .build();
        return toResponse(workShiftRepository.save(shift));
    }

    @Transactional
    public WorkShiftDtos.ShiftResponse closeShift(Long shiftId, WorkShiftDtos.CloseShiftRequest request) {
        MenuWaiter waiter = waiterAccessService.requireCurrentWaiter();
        WorkShift shift = workShiftRepository.findByIdAndBranchId(shiftId, waiter.getBranchId())
                .orElseThrow(() -> new NotFoundException("Vardiya bulunamadı"));
        if (shift.getStatus() != WorkShiftStatus.OPEN) {
            throw new BadRequestException("Vardiya zaten kapalı");
        }
        shift.setStatus(WorkShiftStatus.CLOSED);
        shift.setClosedAt(LocalDateTime.now());
        shift.setClosedByWaiterId(waiter.getId());
        shift.setClosingCash(request.getClosingCash());
        if (request.getNote() != null) {
            shift.setNote(request.getNote());
        }
        return toResponse(workShiftRepository.save(shift));
    }

    @Transactional(readOnly = true)
    public WorkShiftDtos.ShiftResponse currentOpen() {
        MenuWaiter waiter = waiterAccessService.requireCurrentWaiter();
        return workShiftRepository
                .findFirstByBranchIdAndStatusOrderByOpenedAtDesc(waiter.getBranchId(), WorkShiftStatus.OPEN)
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<WorkShiftDtos.ShiftResponse> listForBranch(Long branchId, LocalDateTime from, LocalDateTime to) {
        return workShiftRepository.findByBranchIdAndOpenedAtBetweenOrderByOpenedAtDesc(branchId, from, to).stream()
                .map(this::toResponse)
                .toList();
    }

    private WorkShiftDtos.ShiftResponse toResponse(WorkShift shift) {
        LocalDateTime end = shift.getClosedAt() == null ? LocalDateTime.now() : shift.getClosedAt();
        List<Long> menuIds = resolveMenuIds(shift);
        BigDecimal revenue = BigDecimal.ZERO;
        long orderCount = 0L;
        if (!menuIds.isEmpty()) {
            List<BillPayment> payments = billPaymentRepository.findByMenuIdInAndPaidAtBetween(
                    menuIds, shift.getOpenedAt(), end);
            revenue = payments.stream()
                    .map(BillPayment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);
            orderCount = menuOrderRepository
                    .findByMenuIdInAndCreatedAtBetweenOrderByCreatedAtAsc(menuIds, shift.getOpenedAt(), end)
                    .stream()
                    .filter(o -> o.getConfirmedAt() != null)
                    .count();
        }
        return WorkShiftDtos.ShiftResponse.builder()
                .id(shift.getId())
                .branchId(shift.getBranchId())
                .menuId(shift.getMenuId())
                .openedByWaiterId(shift.getOpenedByWaiterId())
                .closedByWaiterId(shift.getClosedByWaiterId())
                .openedAt(shift.getOpenedAt())
                .closedAt(shift.getClosedAt())
                .openingFloat(shift.getOpeningFloat())
                .closingCash(shift.getClosingCash())
                .note(shift.getNote())
                .status(shift.getStatus().name())
                .waiterIds(shift.getWaiterIds())
                .revenue(revenue)
                .orderCount(orderCount)
                .build();
    }

    private List<Long> resolveMenuIds(WorkShift shift) {
        if (shift.getMenuId() != null) {
            return List.of(shift.getMenuId());
        }
        return menuRepository.findByBranchIdAndDeletedFalse(shift.getBranchId()).stream()
                .map(Menu::getMenuId)
                .toList();
    }
}
