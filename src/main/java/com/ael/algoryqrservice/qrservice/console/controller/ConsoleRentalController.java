package com.ael.algoryqrservice.qrservice.console.controller;

import com.ael.algoryqrservice.qrservice.console.dto.ConsoleDtos;
import com.ael.algoryqrservice.qrservice.console.service.ConsoleCatalogService;
import com.ael.algoryqrservice.qrservice.console.service.ConsoleSessionService;
import com.ael.algoryqrservice.qrservice.console.service.DeviceCustomerService;
import com.ael.algoryqrservice.qrservice.console.service.DeviceLibraryService;
import com.ael.algoryqrservice.qrservice.console.service.DeviceMaintenanceService;
import com.ael.algoryqrservice.qrservice.console.service.DeviceProgramService;
import com.ael.algoryqrservice.qrservice.console.service.DeviceShiftService;
import com.ael.algoryqrservice.qrservice.console.service.DeviceQrService;
import com.ael.algoryqrservice.qrservice.console.service.DeviceTournamentService;
import com.ael.algoryqrservice.qrservice.console.service.DeviceReportService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Oyun & Cihaz Takip, owner side: devices, rates and rules, sessions, customers, reservations and the report.
 * Staff tokens are refused here by {@link com.ael.algoryqrservice.qrservice.security.StaffPathGatewayFilter};
 * staff use {@link StaffDeviceController}.
 */
@RestController
@RequestMapping("/api/v1/console")
@RequiredArgsConstructor
public class ConsoleRentalController {

    private final ConsoleCatalogService catalogService;
    private final ConsoleSessionService sessionService;
    private final DeviceCustomerService customerService;
    private final DeviceReservationService reservationService;
    private final DeviceReportService reportService;
    private final DeviceLibraryService libraryService;
    private final DeviceMaintenanceService maintenanceService;
    private final DeviceProgramService programService;
    private final DeviceShiftService shiftService;
    private final DeviceTournamentService tournamentService;
    private final DeviceQrService deviceQrService;

    // ---- Devices and rates -------------------------------------------------------------------------------------

    @GetMapping("/branches/{branchId}/consoles")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<List<ConsoleDtos.ConsoleResponse>> listConsoles(@PathVariable Long branchId) {
        return ResponseEntity.ok(catalogService.list(branchId));
    }

    @PostMapping("/branches/{branchId}/consoles")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.ConsoleResponse> createConsole(
            @PathVariable Long branchId,
            @Valid @RequestBody ConsoleDtos.ConsoleRequest request
    ) {
        return ResponseEntity.status(201).body(catalogService.create(branchId, request));
    }

    @PutMapping("/consoles/{consoleId}")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.ConsoleResponse> updateConsole(
            @PathVariable Long consoleId,
            @Valid @RequestBody ConsoleDtos.ConsoleRequest request
    ) {
        return ResponseEntity.ok(catalogService.update(consoleId, request));
    }

    @DeleteMapping("/consoles/{consoleId}")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<Void> deleteConsole(@PathVariable Long consoleId) {
        catalogService.delete(consoleId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/branches/{branchId}/rate")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.RateSettingResponse> getRate(@PathVariable Long branchId) {
        return ResponseEntity.ok(catalogService.getRate(branchId));
    }

    @PutMapping("/branches/{branchId}/rate")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.RateSettingResponse> updateRate(
            @PathVariable Long branchId,
            @Valid @RequestBody ConsoleDtos.RateSettingRequest request
    ) {
        return ResponseEntity.ok(catalogService.updateRate(branchId, request));
    }

    @GetMapping("/branches/{branchId}/type-rates")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<List<ConsoleDtos.TypeRate>> getTypeRates(@PathVariable Long branchId) {
        return ResponseEntity.ok(catalogService.getTypeRates(branchId));
    }

    @PutMapping("/branches/{branchId}/type-rates")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<List<ConsoleDtos.TypeRate>> updateTypeRates(
            @PathVariable Long branchId,
            @Valid @RequestBody ConsoleDtos.TypeRatesRequest request
    ) {
        return ResponseEntity.ok(catalogService.updateTypeRates(branchId, request));
    }

    @GetMapping("/branches/{branchId}/tariffs")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<List<ConsoleDtos.TariffResponse>> listTariffs(@PathVariable Long branchId) {
        return ResponseEntity.ok(catalogService.listTariffs(branchId));
    }

    @PostMapping("/branches/{branchId}/tariffs")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.TariffResponse> createTariff(
            @PathVariable Long branchId,
            @Valid @RequestBody ConsoleDtos.TariffRequest request
    ) {
        return ResponseEntity.status(201).body(catalogService.createTariff(branchId, request));
    }

    @PutMapping("/tariffs/{tariffId}")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.TariffResponse> updateTariff(
            @PathVariable Long tariffId,
            @Valid @RequestBody ConsoleDtos.TariffRequest request
    ) {
        return ResponseEntity.ok(catalogService.updateTariff(tariffId, request));
    }

    @DeleteMapping("/tariffs/{tariffId}")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<Void> deleteTariff(@PathVariable Long tariffId) {
        catalogService.deleteTariff(tariffId);
        return ResponseEntity.noContent().build();
    }

    // ---- Sessions ----------------------------------------------------------------------------------------------

    @PostMapping("/consoles/{consoleId}/sessions")
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

    /** Closes the session without charge; it is left out of revenue, shift and analytics totals. */
    @PostMapping("/sessions/{sessionId}/cancel")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.SessionResponse> cancelSession(
            @PathVariable Long sessionId,
            @Valid @RequestBody(required = false) ConsoleDtos.CancelSessionRequest request
    ) {
        return ResponseEntity.ok(sessionService.cancel(sessionId, request));
    }

    /** Corrects a finished session (end time, products, discount, payment, name, note); every change is logged. */
    @PutMapping("/sessions/{sessionId}")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.SessionResponse> editSession(
            @PathVariable Long sessionId,
            @Valid @RequestBody ConsoleDtos.EditSessionRequest request
    ) {
        return ResponseEntity.ok(sessionService.edit(sessionId, request));
    }

    @GetMapping("/sessions/{sessionId}/changes")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<List<ConsoleDtos.SessionChangeResponse>> sessionChanges(@PathVariable Long sessionId) {
        return ResponseEntity.ok(sessionService.changes(sessionId));
    }

    /** Open sessions past their time or left paused, across the merchant's branches; the panel warns about them. */
    @GetMapping("/alerts/overdue")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<List<ConsoleDtos.OverdueSessionResponse>> overdueSessions() {
        return ResponseEntity.ok(sessionService.overdue());
    }

    @GetMapping("/branches/{branchId}/sessions/active")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<List<ConsoleDtos.SessionResponse>> listActive(@PathVariable Long branchId) {
        return ResponseEntity.ok(sessionService.listActive(branchId));
    }

    @GetMapping("/branches/{branchId}/sessions")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.SessionPageResponse> listHistory(
            @PathVariable Long branchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(sessionService.listHistory(branchId, page, size));
    }

    @GetMapping("/branches/{branchId}/products")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<List<ConsoleDtos.ProductOption>> listProducts(@PathVariable Long branchId) {
        return ResponseEntity.ok(sessionService.products(branchId));
    }

    // ---- Customers ---------------------------------------------------------------------------------------------

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

    @PutMapping("/customers/{customerId}")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.CustomerResponse> updateCustomer(
            @PathVariable Long customerId,
            @Valid @RequestBody ConsoleDtos.CustomerRequest request
    ) {
        return ResponseEntity.ok(customerService.update(customerId, request));
    }

    @DeleteMapping("/customers/{customerId}")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long customerId) {
        customerService.delete(customerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/customers/{customerId}/top-up")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.CustomerResponse> topUp(
            @PathVariable Long customerId,
            @Valid @RequestBody ConsoleDtos.TopUpRequest request
    ) {
        return ResponseEntity.ok(customerService.topUp(customerId, request));
    }

    @PostMapping("/customers/{customerId}/adjust")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.CustomerResponse> adjustCustomer(
            @PathVariable Long customerId,
            @Valid @RequestBody ConsoleDtos.AdjustRequest request
    ) {
        return ResponseEntity.ok(customerService.adjust(customerId, request));
    }

    @GetMapping("/customers/{customerId}/transactions")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.CustomerTxnPageResponse> customerTransactions(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(customerService.transactions(customerId, page, size));
    }

    // ---- Reservations ------------------------------------------------------------------------------------------

    @GetMapping("/branches/{branchId}/reservations")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<List<ConsoleDtos.ReservationResponse>> listReservations(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(reservationService.list(branchId, from, to));
    }

    @PostMapping("/branches/{branchId}/reservations")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.ReservationResponse> createReservation(
            @PathVariable Long branchId,
            @Valid @RequestBody ConsoleDtos.ReservationRequest request
    ) {
        return ResponseEntity.status(201).body(reservationService.create(branchId, request));
    }

    @PutMapping("/reservations/{reservationId}")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.ReservationResponse> updateReservation(
            @PathVariable Long reservationId,
            @Valid @RequestBody ConsoleDtos.ReservationRequest request
    ) {
        return ResponseEntity.ok(reservationService.update(reservationId, request));
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

    // ---- Report ------------------------------------------------------------------------------------------------

    @GetMapping("/branches/{branchId}/report")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.ReportResponse> report(
            @PathVariable Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(reportService.report(branchId, from, to));
    }

    // ---- Device extras: QR key, games, maintenance -------------------------------------------------------------

    @GetMapping("/consoles/{consoleId}/qr")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.DeviceQrResponse> deviceQr(@PathVariable Long consoleId) {
        return ResponseEntity.ok(deviceQrService.qr(consoleId));
    }

    @PostMapping("/consoles/{consoleId}/public-token/rotate")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.ConsoleResponse> rotateToken(
            @PathVariable Long consoleId
    ) {
        return ResponseEntity.ok(catalogService.rotateToken(consoleId));
    }

    @PutMapping("/consoles/{consoleId}/games")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.ConsoleResponse> setConsoleGames(
            @PathVariable Long consoleId,
            @Valid @RequestBody ConsoleDtos.ConsoleGamesRequest request
    ) {
        return ResponseEntity.ok(libraryService.setConsoleGames(consoleId, request));
    }

    @PostMapping("/consoles/{consoleId}/maintenance")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.ConsoleResponse> startMaintenance(
            @PathVariable Long consoleId,
            @Valid @RequestBody(required = false) ConsoleDtos.MaintenanceStartRequest request
    ) {
        return ResponseEntity.ok(maintenanceService.start(consoleId, request));
    }

    @PostMapping("/consoles/{consoleId}/maintenance/end")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.ConsoleResponse> endMaintenance(
            @PathVariable Long consoleId,
            @Valid @RequestBody(required = false) ConsoleDtos.MaintenanceEndRequest request
    ) {
        return ResponseEntity.ok(maintenanceService.end(consoleId, request));
    }

    @PostMapping("/consoles/{consoleId}/expenses")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.MaintenanceLogResponse> addExpense(
            @PathVariable Long consoleId,
            @Valid @RequestBody ConsoleDtos.ExpenseRequest request
    ) {
        return ResponseEntity.status(201).body(maintenanceService.expense(consoleId, request));
    }

    @GetMapping("/consoles/{consoleId}/maintenance-logs")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<List<ConsoleDtos.MaintenanceLogResponse>> maintenanceLogs(
            @PathVariable Long consoleId
    ) {
        return ResponseEntity.ok(maintenanceService.logs(consoleId));
    }

    // ---- Game library and packages ---------------------------------------------------------------------------

    @GetMapping("/branches/{branchId}/games")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<List<ConsoleDtos.GameResponse>> listGames(
            @PathVariable Long branchId
    ) {
        return ResponseEntity.ok(libraryService.listGames(branchId));
    }

    @PostMapping("/branches/{branchId}/games")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.GameResponse> createGame(
            @PathVariable Long branchId,
            @Valid @RequestBody ConsoleDtos.GameRequest request
    ) {
        return ResponseEntity.status(201).body(libraryService.createGame(branchId, request));
    }

    @PutMapping("/games/{gameId}")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.GameResponse> updateGame(
            @PathVariable Long gameId,
            @Valid @RequestBody ConsoleDtos.GameRequest request
    ) {
        return ResponseEntity.ok(libraryService.updateGame(gameId, request));
    }

    @DeleteMapping("/games/{gameId}")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<Void> deleteGame(
            @PathVariable Long gameId
    ) {
        libraryService.deleteGame(gameId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/branches/{branchId}/packages")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<List<ConsoleDtos.PackageResponse>> listPackages(
            @PathVariable Long branchId,
            @RequestParam(defaultValue = "false") boolean activeOnly
    ) {
        return ResponseEntity.ok(libraryService.listPackages(branchId, activeOnly));
    }

    @PostMapping("/branches/{branchId}/packages")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.PackageResponse> createPackage(
            @PathVariable Long branchId,
            @Valid @RequestBody ConsoleDtos.PackageRequest request
    ) {
        return ResponseEntity.status(201).body(libraryService.createPackage(branchId, request));
    }

    @PutMapping("/packages/{packageId}")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.PackageResponse> updatePackage(
            @PathVariable Long packageId,
            @Valid @RequestBody ConsoleDtos.PackageRequest request
    ) {
        return ResponseEntity.ok(libraryService.updatePackage(packageId, request));
    }

    @DeleteMapping("/packages/{packageId}")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<Void> deletePackage(
            @PathVariable Long packageId
    ) {
        libraryService.deletePackage(packageId);
        return ResponseEntity.noContent().build();
    }

    // ---- Extension requests from the device QR page -----------------------------------------------------------

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

    // ---- Customer programme ----------------------------------------------------------------------------------

    @GetMapping("/customers/insights")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.CustomerInsightsResponse> customerInsights(
            @RequestParam(required = false) Integer lapsedDays,
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(customerService.insights(lapsedDays, limit));
    }

    @PostMapping("/customers/{customerId}/gift")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.CustomerResponse> giftCustomer(
            @PathVariable Long customerId,
            @Valid @RequestBody ConsoleDtos.GiftRequest request
    ) {
        return ResponseEntity.ok(customerService.gift(customerId, request));
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

    @PostMapping("/memberships/{membershipId}/cancel")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.MembershipResponse> cancelMembership(
            @PathVariable Long membershipId
    ) {
        return ResponseEntity.ok(programService.cancel(membershipId));
    }

    @GetMapping("/program")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.ProgramResponse> getProgram() {
        return ResponseEntity.ok(programService.getProgram());
    }

    @PutMapping("/program")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.ProgramResponse> updateProgram(
            @Valid @RequestBody ConsoleDtos.ProgramRequest request
    ) {
        return ResponseEntity.ok(programService.updateProgram(request));
    }

    @GetMapping("/membership-plans")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<List<ConsoleDtos.MembershipPlanResponse>> listMembershipPlans(
            @RequestParam(defaultValue = "false") boolean activeOnly
    ) {
        return ResponseEntity.ok(programService.listPlans(activeOnly));
    }

    @PostMapping("/membership-plans")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.MembershipPlanResponse> createMembershipPlan(
            @Valid @RequestBody ConsoleDtos.MembershipPlanRequest request
    ) {
        return ResponseEntity.status(201).body(programService.createPlan(request));
    }

    @PutMapping("/membership-plans/{planId}")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.MembershipPlanResponse> updateMembershipPlan(
            @PathVariable Long planId,
            @Valid @RequestBody ConsoleDtos.MembershipPlanRequest request
    ) {
        return ResponseEntity.ok(programService.updatePlan(planId, request));
    }

    @DeleteMapping("/membership-plans/{planId}")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<Void> deleteMembershipPlan(
            @PathVariable Long planId
    ) {
        programService.deletePlan(planId);
        return ResponseEntity.noContent().build();
    }

    // ---- Shifts ----------------------------------------------------------------------------------------------

    @PostMapping("/branches/{branchId}/shifts")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.ShiftResponse> openShift(
            @PathVariable Long branchId,
            @Valid @RequestBody(required = false) ConsoleDtos.OpenShiftRequest request
    ) {
        return ResponseEntity.status(201).body(shiftService.open(branchId, request));
    }

    @GetMapping("/branches/{branchId}/shifts/current")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.ShiftResponse> currentShift(@PathVariable Long branchId) {
        return shiftService.current(branchId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/branches/{branchId}/shifts")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.ShiftPageResponse> listShifts(
            @PathVariable Long branchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(shiftService.list(branchId, page, size));
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

    @GetMapping("/branches/{branchId}/tournaments")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<List<ConsoleDtos.TournamentResponse>> listTournaments(
            @PathVariable Long branchId
    ) {
        return ResponseEntity.ok(tournamentService.list(branchId));
    }

    @GetMapping("/tournaments/{tournamentId}")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.TournamentResponse> getTournament(
            @PathVariable Long tournamentId
    ) {
        return ResponseEntity.ok(tournamentService.get(tournamentId));
    }

    @PostMapping("/branches/{branchId}/tournaments")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.TournamentResponse> createTournament(
            @PathVariable Long branchId,
            @Valid @RequestBody ConsoleDtos.TournamentRequest request
    ) {
        return ResponseEntity.status(201).body(tournamentService.create(branchId, request));
    }

    @PutMapping("/tournaments/{tournamentId}")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.TournamentResponse> updateTournament(
            @PathVariable Long tournamentId,
            @Valid @RequestBody ConsoleDtos.TournamentRequest request
    ) {
        return ResponseEntity.ok(tournamentService.update(tournamentId, request));
    }

    @PostMapping("/tournaments/{tournamentId}/cancel")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.TournamentResponse> cancelTournament(
            @PathVariable Long tournamentId
    ) {
        return ResponseEntity.ok(tournamentService.cancel(tournamentId));
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

    // ---- Profitability ---------------------------------------------------------------------------------------

    @GetMapping("/branches/{branchId}/profitability")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.ProfitabilityResponse> profitability(
            @PathVariable Long branchId
    ) {
        return ResponseEntity.ok(reportService.profitability(branchId));
    }
}
