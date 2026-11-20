package com.ael.algoryqrservice.qrservice.console.service;

import com.ael.algoryqrservice.qrservice.console.domain.DeviceCustomerTxnType;
import com.ael.algoryqrservice.qrservice.console.domain.DevicePaymentMethod;
import com.ael.algoryqrservice.qrservice.console.dto.ConsoleDtos;
import com.ael.algoryqrservice.qrservice.model.Branch;
import com.ael.algoryqrservice.qrservice.model.ConsoleSession;
import com.ael.algoryqrservice.qrservice.model.DeviceShift;
import com.ael.algoryqrservice.qrservice.model.DeviceTournamentPlayer;
import com.ael.algoryqrservice.qrservice.repository.ConsoleSessionRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceCustomerTxnRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceMembershipRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceShiftRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceTournamentPlayerRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceTournamentRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import com.ael.algoryqrservice.staffservice.infrastructure.persistence.entity.MerchantStaff;
import com.ael.algoryqrservice.staffservice.infrastructure.persistence.repository.MerchantStaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Cash shifts at the counter. A person (a staff member, or the owner with no staff id) opens a shift with the cash
 * in the drawer and closes it by counting; the money they took in between is worked out from what they did:
 * sessions they closed, balance top-ups, membership sales and tournament fees. Nothing is stored twice; a closed
 * shift's figures are worked out again from the same records.
 */
@Service
@RequiredArgsConstructor
public class DeviceShiftService {

    private final DeviceShiftRepository shiftRepository;
    private final ConsoleSessionRepository sessionRepository;
    private final DeviceCustomerTxnRepository txnRepository;
    private final DeviceMembershipRepository membershipRepository;
    private final DeviceTournamentPlayerRepository playerRepository;
    private final DeviceTournamentRepository tournamentRepository;
    private final MerchantStaffRepository staffRepository;
    private final ConsoleAccess access;

    @Transactional
    public ConsoleDtos.ShiftResponse open(Long branchId, ConsoleDtos.OpenShiftRequest request) {
        Branch branch = access.requireBranch(branchId);
        Long staffId = access.staffId();
        if (shiftRepository.findOpen(branch.getId(), branch.getMerchantId(), staffId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Açık bir vardiyanız var; önce kapatın");
        }
        BigDecimal opening = request == null || request.openingCash() == null
                ? BigDecimal.ZERO
                : request.openingCash();
        try {
            DeviceShift saved = shiftRepository.saveAndFlush(DeviceShift.builder()
                    .merchantId(branch.getMerchantId())
                    .branchId(branch.getId())
                    .staffId(staffId)
                    .openedAt(AppTime.nowLocal())
                    .openingCash(opening.setScale(2, RoundingMode.HALF_UP))
                    .deleted(false)
                    .build());
            return response(saved);
        } catch (DataIntegrityViolationException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Açık bir vardiyanız var; önce kapatın");
        }
    }

    /** The caller's open shift at the branch, if any. */
    @Transactional(readOnly = true)
    public Optional<ConsoleDtos.ShiftResponse> current(Long branchId) {
        Branch branch = access.requireBranch(branchId);
        return shiftRepository.findOpen(branch.getId(), branch.getMerchantId(), access.staffId()).map(this::response);
    }

    @Transactional
    public ConsoleDtos.ShiftResponse close(Long shiftId, ConsoleDtos.CloseShiftRequest request) {
        DeviceShift shift = requireOwn(shiftId);
        if (shift.getClosedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vardiya zaten kapalı");
        }
        LocalDateTime now = AppTime.nowLocal();
        Totals totals = totals(shift, now);
        shift.setClosedAt(now);
        shift.setExpectedCash(shift.getOpeningCash().add(totals.cashIn()).setScale(2, RoundingMode.HALF_UP));
        shift.setCountedCash(request.countedCash().setScale(2, RoundingMode.HALF_UP));
        shift.setNote(DeviceMappers.blankToNull(request.note()));
        return response(shiftRepository.save(shift));
    }

    /** Owner: every shift of the branch. Staff: only their own. */
    @Transactional(readOnly = true)
    public ConsoleDtos.ShiftPageResponse list(Long branchId, int page, int size) {
        Branch branch = access.requireBranch(branchId);
        Page<DeviceShift> result = shiftRepository.findByBranchIdAndMerchantIdAndDeletedFalseOrderByOpenedAtDescIdDesc(
                branch.getId(), branch.getMerchantId(), PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50)));
        Long staffId = access.staffId();
        List<ConsoleDtos.ShiftResponse> content = result.getContent().stream()
                .filter(shift -> staffId == null || Objects.equals(shift.getStaffId(), staffId))
                .map(this::response)
                .toList();
        return new ConsoleDtos.ShiftPageResponse(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    private DeviceShift requireOwn(Long shiftId) {
        DeviceShift shift = shiftRepository.findByIdAndMerchantIdAndDeletedFalse(shiftId, access.merchantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vardiya bulunamadı"));
        access.checkBranch(shift.getBranchId());
        Long staffId = access.staffId();
        if (staffId != null && !Objects.equals(shift.getStaffId(), staffId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vardiya bulunamadı");
        }
        return shift;
    }

    // ---- Figures -----------------------------------------------------------------------------------------------

    private record Totals(
            int sessions,
            BigDecimal sessionsTotal,
            Map<DevicePaymentMethod, Acc> sessionPayments,
            Map<DevicePaymentMethod, Acc> topUps,
            Map<DevicePaymentMethod, Acc> memberships,
            Map<DevicePaymentMethod, Acc> fees,
            BigDecimal cashIn
    ) {
    }

    private static final class Acc {
        int count;
        BigDecimal amount = BigDecimal.ZERO;

        void add(BigDecimal value) {
            count++;
            amount = amount.add(value == null ? BigDecimal.ZERO : value);
        }
    }

    private Totals totals(DeviceShift shift, LocalDateTime until) {
        LocalDateTime from = shift.getOpenedAt();
        Long staffId = shift.getStaffId();
        Map<DevicePaymentMethod, Acc> sessionPayments = new EnumMap<>(DevicePaymentMethod.class);
        int sessions = 0;
        BigDecimal sessionsTotal = BigDecimal.ZERO;
        for (ConsoleSession session : sessionRepository.findClosedAtBranch(shift.getBranchId(), from, until)) {
            if (!Objects.equals(session.getClosedByStaffId(), staffId)) {
                continue;
            }
            sessions++;
            sessionsTotal = sessionsTotal.add(nz(session.getAmount()));
            DevicePaymentMethod method = session.getPaymentMethod() == null ? DevicePaymentMethod.CASH : session.getPaymentMethod();
            sessionPayments.computeIfAbsent(method, m -> new Acc()).add(session.getAmount());
        }
        Map<DevicePaymentMethod, Acc> topUps = new EnumMap<>(DevicePaymentMethod.class);
        txnRepository.findAtBranch(shift.getBranchId(), from, until).stream()
                .filter(t -> t.getTxnType() == DeviceCustomerTxnType.TOPUP && Objects.equals(t.getStaffId(), staffId))
                .forEach(t -> topUps.computeIfAbsent(
                        t.getPaymentMethod() == null ? DevicePaymentMethod.CASH : t.getPaymentMethod(), m -> new Acc()).add(t.getAmount()));
        Map<DevicePaymentMethod, Acc> memberships = new EnumMap<>(DevicePaymentMethod.class);
        membershipRepository.findSoldAtBranch(shift.getBranchId(), from, until).stream()
                .filter(m -> Objects.equals(m.getStaffId(), staffId))
                .forEach(m -> memberships.computeIfAbsent(m.getPaymentMethod(), k -> new Acc()).add(m.getPrice()));
        Map<DevicePaymentMethod, Acc> fees = new EnumMap<>(DevicePaymentMethod.class);
        List<DeviceTournamentPlayer> paid = playerRepository.findPaidAtBranch(shift.getBranchId(), from, until).stream()
                .filter(p -> Objects.equals(p.getStaffId(), staffId))
                .toList();
        if (!paid.isEmpty()) {
            Map<Long, BigDecimal> feeOf = new HashMap<>();
            tournamentRepository.findAllById(paid.stream().map(DeviceTournamentPlayer::getTournamentId).distinct().toList())
                    .forEach(t -> feeOf.put(t.getId(), t.getEntryFee()));
            paid.forEach(p -> fees.computeIfAbsent(
                    p.getPaymentMethod() == null ? DevicePaymentMethod.CASH : p.getPaymentMethod(), m -> new Acc())
                    .add(feeOf.get(p.getTournamentId())));
        }
        BigDecimal cash = BigDecimal.ZERO;
        for (Map<DevicePaymentMethod, Acc> map : List.of(sessionPayments, topUps, memberships, fees)) {
            Acc acc = map.get(DevicePaymentMethod.CASH);
            if (acc != null) {
                cash = cash.add(acc.amount);
            }
        }
        return new Totals(sessions, sessionsTotal, sessionPayments, topUps, memberships, fees, cash);
    }

    private ConsoleDtos.ShiftResponse response(DeviceShift shift) {
        LocalDateTime until = shift.getClosedAt() == null ? AppTime.nowLocal() : shift.getClosedAt();
        Totals totals = totals(shift, until);
        BigDecimal expected = shift.getClosedAt() != null && shift.getExpectedCash() != null
                ? shift.getExpectedCash()
                : shift.getOpeningCash().add(totals.cashIn()).setScale(2, RoundingMode.HALF_UP);
        String staffName = shift.getStaffId() == null
                ? "İşletme sahibi"
                : staffRepository.findById(shift.getStaffId()).map(MerchantStaff::getDisplayName).orElse("Personel #" + shift.getStaffId());
        return new ConsoleDtos.ShiftResponse(
                shift.getId(),
                shift.getBranchId(),
                shift.getStaffId(),
                staffName,
                shift.getOpenedAt(),
                shift.getClosedAt(),
                shift.getOpeningCash(),
                totals.sessions(),
                totals.sessionsTotal().setScale(2, RoundingMode.HALF_UP),
                rows(totals.sessionPayments()),
                rows(totals.topUps()),
                rows(totals.memberships()),
                rows(totals.fees()),
                totals.cashIn().setScale(2, RoundingMode.HALF_UP),
                expected,
                shift.getCountedCash(),
                shift.getCountedCash() == null ? null : shift.getCountedCash().subtract(expected).setScale(2, RoundingMode.HALF_UP),
                shift.getNote()
        );
    }

    private static List<ConsoleDtos.PaymentTotal> rows(Map<DevicePaymentMethod, Acc> map) {
        return map.entrySet().stream()
                .map(e -> new ConsoleDtos.PaymentTotal(e.getKey(), e.getValue().count,
                        e.getValue().amount.setScale(2, RoundingMode.HALF_UP)))
                .toList();
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
