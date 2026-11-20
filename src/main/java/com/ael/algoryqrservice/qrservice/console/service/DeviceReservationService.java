package com.ael.algoryqrservice.qrservice.console.service;

import com.ael.algoryqrservice.qrservice.console.domain.DeviceReservationStatus;
import com.ael.algoryqrservice.qrservice.console.dto.ConsoleDtos;
import com.ael.algoryqrservice.qrservice.model.Branch;
import com.ael.algoryqrservice.qrservice.model.DeviceCustomer;
import com.ael.algoryqrservice.qrservice.model.DeviceReservation;
import com.ael.algoryqrservice.qrservice.model.GameConsole;
import com.ael.algoryqrservice.qrservice.repository.DeviceCustomerRepository;
import com.ael.algoryqrservice.qrservice.repository.DeviceReservationRepository;
import com.ael.algoryqrservice.qrservice.repository.GameConsoleRepository;
import com.ael.algoryqrservice.qrservice.util.AppTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Booking a device for a time slot, and turning the booking into a session when the customer arrives. */
@Service
@RequiredArgsConstructor
public class DeviceReservationService {

    private final DeviceReservationRepository reservationRepository;
    private final GameConsoleRepository consoleRepository;
    private final DeviceCustomerRepository customerRepository;
    private final ConsoleSessionService sessionService;
    private final ConsoleAccess access;

    /** Reservations overlapping the days {@code from}..{@code to} (inclusive), every status. */
    @Transactional(readOnly = true)
    public List<ConsoleDtos.ReservationResponse> list(Long branchId, LocalDate from, LocalDate to) {
        Branch branch = access.requireBranch(branchId);
        LocalDate start = from == null ? AppTime.nowLocal().toLocalDate() : from;
        LocalDate end = to == null || to.isBefore(start) ? start.plusDays(6) : to;
        if (end.isAfter(start.plusDays(62))) {
            end = start.plusDays(62);
        }
        List<DeviceReservation> rows = reservationRepository.findOverlappingForBranch(
                branch.getId(), branch.getMerchantId(), start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        Map<Long, String> names = consoleRepository
                .findByIdIn(rows.stream().map(DeviceReservation::getConsoleId).distinct().toList()).stream()
                .collect(Collectors.toMap(GameConsole::getId, GameConsole::getName, (a, b) -> a));
        return rows.stream().map(r -> DeviceMappers.reservation(r, names.get(r.getConsoleId()))).toList();
    }

    @Transactional
    public ConsoleDtos.ReservationResponse create(Long branchId, ConsoleDtos.ReservationRequest request) {
        Branch branch = access.requireBranch(branchId);
        GameConsole console = consoleRepository.lockOwned(request.consoleId(), branch.getMerchantId())
                .filter(c -> c.getBranchId().equals(branch.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cihaz bulunamadı"));
        DeviceReservation reservation = DeviceReservation.builder()
                .merchantId(branch.getMerchantId())
                .branchId(branch.getId())
                .status(DeviceReservationStatus.BOOKED)
                .deleted(false)
                .build();
        apply(reservation, console, request, -1L);
        return DeviceMappers.reservation(reservationRepository.save(reservation), console.getName());
    }

    @Transactional
    public ConsoleDtos.ReservationResponse update(Long reservationId, ConsoleDtos.ReservationRequest request) {
        DeviceReservation reservation = lockBooked(reservationId);
        GameConsole console = consoleRepository.lockOwned(request.consoleId(), reservation.getMerchantId())
                .filter(c -> c.getBranchId().equals(reservation.getBranchId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cihaz bulunamadı"));
        apply(reservation, console, request, reservation.getId());
        return DeviceMappers.reservation(reservationRepository.save(reservation), console.getName());
    }

    @Transactional
    public ConsoleDtos.ReservationResponse cancel(Long reservationId) {
        return close(reservationId, DeviceReservationStatus.CANCELLED);
    }

    @Transactional
    public ConsoleDtos.ReservationResponse noShow(Long reservationId) {
        return close(reservationId, DeviceReservationStatus.NO_SHOW);
    }

    /** Opens a session on the reserved device; the reserved length becomes the planned time. */
    @Transactional
    public ConsoleDtos.SessionResponse start(Long reservationId, ConsoleDtos.StartReservationRequest request) {
        DeviceReservation reservation = lockBooked(reservationId);
        GameConsole console = consoleRepository.lockOwned(reservation.getConsoleId(), reservation.getMerchantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cihaz bulunamadı"));
        int planned = (int) Math.max(Duration.between(reservation.getStartsAt(), reservation.getEndsAt()).toMinutes(), 1);
        ConsoleDtos.SessionResponse session = sessionService.startOn(console, new ConsoleDtos.StartSessionRequest(
                reservation.getCustomerName(),
                reservation.getNote(),
                reservation.getCustomerId(),
                planned,
                request == null ? null : request.controllerCount()
        ), reservation.getId());
        reservation.setStatus(DeviceReservationStatus.STARTED);
        reservation.setSessionId(session.id());
        reservationRepository.save(reservation);
        return session;
    }

    private ConsoleDtos.ReservationResponse close(Long reservationId, DeviceReservationStatus status) {
        DeviceReservation reservation = lockBooked(reservationId);
        reservation.setStatus(status);
        DeviceReservation saved = reservationRepository.save(reservation);
        String name = consoleRepository.findById(saved.getConsoleId()).map(GameConsole::getName).orElse(null);
        return DeviceMappers.reservation(saved, name);
    }

    private void apply(DeviceReservation reservation, GameConsole console, ConsoleDtos.ReservationRequest request, Long excludeId) {
        LocalDateTime startsAt = request.startsAt().withSecond(0).withNano(0);
        LocalDateTime endsAt = startsAt.plusMinutes(request.durationMinutes());
        if (endsAt.isBefore(AppTime.nowLocal())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geçmiş bir saate rezervasyon yapılamaz");
        }
        if (reservationRepository.existsOverlap(console.getId(), DeviceReservationStatus.BOOKED, startsAt, endsAt, excludeId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu cihaz o saatlerde rezerve");
        }
        DeviceCustomer customer = null;
        if (request.customerId() != null) {
            customer = customerRepository.findByIdAndMerchantIdAndDeletedFalse(request.customerId(), console.getMerchantId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Müşteri bulunamadı"));
        }
        String name = DeviceMappers.blankToNull(request.customerName());
        if (name == null && customer != null) {
            name = customer.getName();
        }
        if (name == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Müşteri adı girin");
        }
        String phone = DeviceMappers.normalizePhone(request.phone());
        if (phone == null && customer != null) {
            phone = customer.getPhone();
        }
        reservation.setConsoleId(console.getId());
        reservation.setCustomerId(customer == null ? null : customer.getId());
        reservation.setCustomerName(name);
        reservation.setPhone(phone);
        reservation.setNote(DeviceMappers.blankToNull(request.note()));
        reservation.setStartsAt(startsAt);
        reservation.setEndsAt(endsAt);
    }

    private DeviceReservation lockBooked(Long reservationId) {
        DeviceReservation reservation = reservationRepository.lockOwned(reservationId, access.merchantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rezervasyon bulunamadı"));
        access.checkBranch(reservation.getBranchId());
        if (reservation.getStatus() != DeviceReservationStatus.BOOKED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Rezervasyon artık açık değil");
        }
        return reservation;
    }
}
