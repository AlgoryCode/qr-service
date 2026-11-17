package com.ael.algoryqrservice.qrservice.branch.controller;

import com.ael.algoryqrservice.qrservice.menu.dto.MenuDtos;
import com.ael.algoryqrservice.qrservice.menu.service.MenuReservationService;
import com.ael.algoryqrservice.qrservice.security.FeatureKey;
import com.ael.algoryqrservice.qrservice.security.RequiresFeature;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Reservations of a branch (the venue), whichever of its menus they were booked from. */
@RestController
@RequestMapping("/api/v1/branches/{branchId}/reservations")
@RequiredArgsConstructor
public class BranchReservationController {

    private final MenuReservationService menuReservationService;

    @GetMapping
    @RequiresFeature(FeatureKey.QR_MENU)
    public ResponseEntity<MenuDtos.ReservationPageResponse> list(
            @PathVariable Long branchId,
            @RequestParam(required = false, defaultValue = "all") String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(menuReservationService.listForBranch(branchId, status, from, to, q, page, size));
    }

    /** A reservation the business takes itself (phone, walk-in); it is confirmed straight away. */
    @PostMapping
    @RequiresFeature(FeatureKey.QR_MENU)
    public ResponseEntity<MenuDtos.ReservationResponse> create(
            @PathVariable Long branchId,
            @Valid @RequestBody MenuDtos.ReservationCreateRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                menuReservationService.createForBranch(
                        branchId,
                        request,
                        menuReservationService.extractIpAddress(httpRequest)
                )
        );
    }

    @PatchMapping("/{reservationId}")
    @RequiresFeature(FeatureKey.QR_MENU)
    public ResponseEntity<MenuDtos.ReservationResponse> update(
            @PathVariable Long branchId,
            @PathVariable Long reservationId,
            @Valid @RequestBody MenuDtos.ReservationUpdateRequest request
    ) {
        return ResponseEntity.ok(menuReservationService.updateForBranch(branchId, reservationId, request));
    }
}
