package com.ael.algoryqrservice.qrservice.console.controller;

import com.ael.algoryqrservice.qrservice.console.dto.ConsoleDtos;
import com.ael.algoryqrservice.qrservice.console.service.ConsoleAccess;
import com.ael.algoryqrservice.qrservice.console.service.ConsoleCatalogService;
import com.ael.algoryqrservice.qrservice.console.service.ConsoleSessionService;
import com.ael.algoryqrservice.qrservice.console.service.DeviceCustomerService;
import com.ael.algoryqrservice.qrservice.console.service.DeviceLibraryService;
import com.ael.algoryqrservice.qrservice.console.service.DeviceMaintenanceService;
import com.ael.algoryqrservice.qrservice.console.service.DeviceProgramService;
import com.ael.algoryqrservice.qrservice.console.service.DeviceShiftService;
import com.ael.algoryqrservice.qrservice.console.service.DeviceTournamentService;
import com.ael.algoryqrservice.qrservice.console.service.DeviceReservationService;
import com.ael.algoryqrservice.qrservice.security.FeatureKey;
import com.ael.algoryqrservice.qrservice.security.RequiresFeature;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Oyun & Cihaz Takip for staff at the counter: the day-to-day operations on their own branch's devices. Device,
 * rate and rule management, balance corrections and the report stay with the owner.
 */
@RestController
@RequestMapping("/api/v1/staff/devices")
@RequiredArgsConstructor
public class StaffDeviceController {

    private final ConsoleAccess access;
    private final ConsoleCatalogService catalogService;
    private final ConsoleSessionService sessionService;
    private final DeviceCustomerService customerService;
    private final DeviceReservationService reservationService;
    private final DeviceLibraryService libraryService;
    private final DeviceMaintenanceService maintenanceService;
    private final DeviceProgramService programService;
    private final DeviceShiftService shiftService;
    private final DeviceTournamentService tournamentService;

    @GetMapping
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<List<ConsoleDtos.ConsoleResponse>> listConsoles() {
        return ResponseEntity.ok(catalogService.list(access.staffBranchId()));
    }

    @GetMapping("/sessions/active")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<List<ConsoleDtos.SessionResponse>> listActive() {
        return ResponseEntity.ok(sessionService.listActive(access.staffBranchId()));
    }

    @GetMapping("/sessions")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.SessionPageResponse> listHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(sessionService.listHistory(access.staffBranchId(), page, size));
    }

    @GetMapping("/products")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<List<ConsoleDtos.ProductOption>> listProducts() {
        return ResponseEntity.ok(sessionService.products(access.staffBranchId()));
    }

    @PostMapping("/{consoleId}/sessions")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.SessionResponse> startSession(
            @PathVariable Long consoleId,
            @Valid @RequestBody(required = false) ConsoleDtos.StartSessionRequest request
    ) {
        return ResponseEntity.status(201).body(sessionService.start(consoleId, request));
    }

    @PostMapping("/sessions/{sessionId}/pause")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.SessionResponse> pauseSession(@PathVariable Long sessionId) {
        return ResponseEntity.ok(sessionService.pause(sessionId));
    }

    @PostMapping("/sessions/{sessionId}/resume")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.SessionResponse> resumeSession(@PathVariable Long sessionId) {
        return ResponseEntity.ok(sessionService.resume(sessionId));
    }

    @PostMapping("/sessions/{sessionId}/transfer")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.SessionResponse> transferSession(
            @PathVariable Long sessionId,
            @Valid @RequestBody ConsoleDtos.TransferRequest request
    ) {
        return ResponseEntity.ok(sessionService.transfer(sessionId, request));
    }

    @PostMapping("/sessions/{sessionId}/extend")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.SessionResponse> extendSession(
            @PathVariable Long sessionId,
            @Valid @RequestBody ConsoleDtos.ExtendRequest request
    ) {
        return ResponseEntity.ok(sessionService.extend(sessionId, request));
    }

    @PostMapping("/sessions/{sessionId}/items")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.SessionResponse> addItem(
            @PathVariable Long sessionId,
            @Valid @RequestBody ConsoleDtos.AddItemRequest request
    ) {
        return ResponseEntity.ok(sessionService.addItem(sessionId, request));
    }

    @DeleteMapping("/sessions/{sessionId}/items/{itemId}")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.SessionResponse> removeItem(@PathVariable Long sessionId, @PathVariable Long itemId) {
        return ResponseEntity.ok(sessionService.removeItem(sessionId, itemId));
    }

    @GetMapping("/sessions/{sessionId}/quote")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.SessionQuoteResponse> quoteSession(
            @PathVariable Long sessionId,
            @RequestParam(required = false) Boolean useLoyaltyMinutes,
            @RequestParam(required = false) Boolean useMembershipMinutes,
            @RequestParam(required = false) Boolean useBonusBalance,
            @RequestParam(required = false) java.math.BigDecimal discountAmount,
            @RequestParam(required = false) java.math.BigDecimal discountPercent
    ) {
        return ResponseEntity.ok(sessionService.quote(sessionId, new ConsoleDtos.FinishSessionRequest(null, discountAmount, discountPercent, useLoyaltyMinutes, useMembershipMinutes, useBonusBalance)));
    }

    @PostMapping("/sessions/{sessionId}/finish")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.SessionResponse> finishSession(
            @PathVariable Long sessionId,
            @Valid @RequestBody(required = false) ConsoleDtos.FinishSessionRequest request
    ) {
        return ResponseEntity.ok(sessionService.finish(sessionId, request));
    }

    @GetMapping("/customers")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.CustomerPageResponse> searchCustomers(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(customerService.search(q, page, size));
    }

    @PostMapping("/customers")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.CustomerResponse> createCustomer(@Valid @RequestBody ConsoleDtos.CustomerRequest request) {
        return ResponseEntity.status(201).body(customerService.create(request));
    }

    @GetMapping("/customers/{customerId}")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.CustomerResponse> getCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(customerService.get(customerId));
    }

    @PostMapping("/customers/{customerId}/top-up")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.CustomerResponse> topUp(
            @PathVariable Long customerId,
            @Valid @RequestBody ConsoleDtos.TopUpRequest request
    ) {
        return ResponseEntity.ok(customerService.topUp(customerId, request));
    }

    @GetMapping("/reservations")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<List<ConsoleDtos.ReservationResponse>> listReservations(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(reservationService.list(access.staffBranchId(), from, to));
    }

    @PostMapping("/reservations")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.ReservationResponse> createReservation(
            @Valid @RequestBody ConsoleDtos.ReservationRequest request
    ) {
        return ResponseEntity.status(201).body(reservationService.create(access.staffBranchId(), request));
    }

    @PostMapping("/reservations/{reservationId}/cancel")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.ReservationResponse> cancelReservation(@PathVariable Long reservationId) {
        return ResponseEntity.ok(reservationService.cancel(reservationId));
    }

    @PostMapping("/reservations/{reservationId}/no-show")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.ReservationResponse> noShowReservation(@PathVariable Long reservationId) {
        return ResponseEntity.ok(reservationService.noShow(reservationId));
    }

    @PostMapping("/reservations/{reservationId}/start")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.SessionResponse> startReservation(
            @PathVariable Long reservationId,
            @Valid @RequestBody(required = false) ConsoleDtos.StartReservationRequest request
    ) {
        return ResponseEntity.status(201).body(reservationService.start(reservationId, request));
    }

    // ---- Library, packages, maintenance ----------------------------------------------------------------------

    @GetMapping("/games")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<List<ConsoleDtos.GameResponse>> listGames() {
        return ResponseEntity.ok(libraryService.listGames(access.staffBranchId()));
    }

    @GetMapping("/packages")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<List<ConsoleDtos.PackageResponse>> listPackages() {
        return ResponseEntity.ok(libraryService.listPackages(access.staffBranchId(), true));
    }

    @PostMapping("/{consoleId}/maintenance")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.ConsoleResponse> startMaintenance(
            @PathVariable Long consoleId,
            @Valid @RequestBody(required = false) ConsoleDtos.MaintenanceStartRequest request
    ) {
        return ResponseEntity.ok(maintenanceService.start(consoleId, request));
    }

    @PostMapping("/{consoleId}/maintenance/end")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.ConsoleResponse> endMaintenance(
            @PathVariable Long consoleId,
            @Valid @RequestBody(required = false) ConsoleDtos.MaintenanceEndRequest request
    ) {
        return ResponseEntity.ok(maintenanceService.end(consoleId, request));
    }

    @PostMapping("/sessions/{sessionId}/extension-request/approve")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.SessionResponse> approveExtension(
            @PathVariable Long sessionId
    ) {
        return ResponseEntity.ok(sessionService.approveExtension(sessionId));
    }

    @PostMapping("/sessions/{sessionId}/extension-request/dismiss")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.SessionResponse> dismissExtension(
            @PathVariable Long sessionId
    ) {
        return ResponseEntity.ok(sessionService.dismissExtension(sessionId));
    }

    // ---- Memberships ---------------------------------------------------------------------------------------

    @GetMapping("/membership-plans")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<List<ConsoleDtos.MembershipPlanResponse>> listMembershipPlans() {
        return ResponseEntity.ok(programService.listPlans(true));
    }

    @GetMapping("/customers/{customerId}/memberships")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<List<ConsoleDtos.MembershipResponse>> customerMemberships(
            @PathVariable Long customerId
    ) {
        return ResponseEntity.ok(programService.memberships(customerId));
    }

    @PostMapping("/customers/{customerId}/memberships")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.MembershipResponse> sellMembership(
            @PathVariable Long customerId,
            @Valid @RequestBody ConsoleDtos.SellMembershipRequest request
    ) {
        return ResponseEntity.status(201).body(programService.sell(customerId, request));
    }

    // ---- Shifts ----------------------------------------------------------------------------------------------

    @PostMapping("/shifts")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.ShiftResponse> openShift(
            @Valid @RequestBody(required = false) ConsoleDtos.OpenShiftRequest request
    ) {
        return ResponseEntity.status(201).body(shiftService.open(access.staffBranchId(), request));
    }

    @GetMapping("/shifts/current")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.ShiftResponse> currentShift() {
        return shiftService.current(access.staffBranchId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/shifts")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.ShiftPageResponse> listShifts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(shiftService.list(access.staffBranchId(), page, size));
    }

    @PostMapping("/shifts/{shiftId}/close")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.ShiftResponse> closeShift(
            @PathVariable Long shiftId,
            @Valid @RequestBody ConsoleDtos.CloseShiftRequest request
    ) {
        return ResponseEntity.ok(shiftService.close(shiftId, request));
    }

    // ---- Tournaments -----------------------------------------------------------------------------------------

    @GetMapping("/tournaments")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<List<ConsoleDtos.TournamentResponse>> listTournaments() {
        return ResponseEntity.ok(tournamentService.list(access.staffBranchId()));
    }

    @GetMapping("/tournaments/{tournamentId}")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.TournamentResponse> getTournament(
            @PathVariable Long tournamentId
    ) {
        return ResponseEntity.ok(tournamentService.get(tournamentId));
    }

    @PostMapping("/tournaments/{tournamentId}/players")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.TournamentResponse> addTournamentPlayer(
            @PathVariable Long tournamentId,
            @Valid @RequestBody ConsoleDtos.TournamentPlayerRequest request
    ) {
        return ResponseEntity.ok(tournamentService.addPlayer(tournamentId, request));
    }

    @DeleteMapping("/tournaments/{tournamentId}/players/{playerId}")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.TournamentResponse> removeTournamentPlayer(
            @PathVariable Long tournamentId,
            @PathVariable Long playerId
    ) {
        return ResponseEntity.ok(tournamentService.removePlayer(tournamentId, playerId));
    }

    @PostMapping("/tournaments/{tournamentId}/players/{playerId}/pay")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.TournamentResponse> payTournamentPlayer(
            @PathVariable Long tournamentId,
            @PathVariable Long playerId,
            @Valid @RequestBody(required = false) ConsoleDtos.TournamentPayRequest request
    ) {
        return ResponseEntity.ok(tournamentService.pay(tournamentId, playerId, request));
    }

    @PostMapping("/tournaments/{tournamentId}/start")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.TournamentResponse> startTournament(
            @PathVariable Long tournamentId
    ) {
        return ResponseEntity.ok(tournamentService.start(tournamentId));
    }

    @PostMapping("/tournaments/{tournamentId}/matches/{matchId}/result")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.TournamentResponse> reportMatch(
            @PathVariable Long tournamentId,
            @PathVariable Long matchId,
            @Valid @RequestBody ConsoleDtos.MatchResultRequest request
    ) {
        return ResponseEntity.ok(tournamentService.report(tournamentId, matchId, request));
    }
}
