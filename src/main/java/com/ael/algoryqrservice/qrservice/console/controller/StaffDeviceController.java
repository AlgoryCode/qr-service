package com.ael.algoryqrservice.qrservice.console.controller;

import com.ael.algoryqrservice.qrservice.console.dto.ConsoleDtos;
import com.ael.algoryqrservice.qrservice.console.service.ConsoleAccess;
import com.ael.algoryqrservice.qrservice.console.service.ConsoleCatalogService;
import com.ael.algoryqrservice.qrservice.console.service.ConsoleSessionService;
import com.ael.algoryqrservice.qrservice.console.service.DeviceCustomerService;
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
}
