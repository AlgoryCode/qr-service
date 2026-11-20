package com.ael.algoryqrservice.qrservice.console.controller;

import com.ael.algoryqrservice.qrservice.console.dto.ConsoleDtos;
import com.ael.algoryqrservice.qrservice.console.service.ConsoleCatalogService;
import com.ael.algoryqrservice.qrservice.console.service.ConsoleSessionService;
import com.ael.algoryqrservice.qrservice.console.service.DeviceCustomerService;
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
    public ResponseEntity<ConsoleDtos.SessionQuoteResponse> quoteSession(@PathVariable Long sessionId) {
        return ResponseEntity.ok(sessionService.quote(sessionId));
    }

    @PostMapping("/sessions/{sessionId}/finish")
    @RequiresFeature(FeatureKey.CONSOLE_RENTAL)
    public ResponseEntity<ConsoleDtos.SessionResponse> finishSession(
            @PathVariable Long sessionId,
            @Valid @RequestBody(required = false) ConsoleDtos.FinishSessionRequest request
    ) {
        return ResponseEntity.ok(sessionService.finish(sessionId, request));
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
}
