package com.ael.algoryqrservice.controller.admin;

import com.ael.algoryqrservice.coupon.CouponUseCases;
import com.ael.algoryqrservice.model.dto.CouponDtos;
import com.ael.algoryqrservice.model.enums.CouponStatus;
import com.ael.algoryqrservice.util.DashboardSecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/coupons")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCouponController {

    private final CouponUseCases couponUseCases;
    private final DashboardSecurityUtils dashboardSecurityUtils;

    @PostMapping
    public ResponseEntity<CouponDtos.Response> create(@Valid @RequestBody CouponDtos.CreateRequest request) {
        Long adminId = dashboardSecurityUtils.getCurrentDashboardUser().getId();
        return ResponseEntity.status(HttpStatus.CREATED).body(couponUseCases.create(request, adminId));
    }

    @GetMapping
    public ResponseEntity<CouponDtos.PageResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) CouponStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(couponUseCases.list(q, status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CouponDtos.Response> getById(@PathVariable Long id) {
        return ResponseEntity.ok(couponUseCases.getById(id));
    }

    @GetMapping("/{id}/logs")
    public ResponseEntity<CouponDtos.LogPageResponse> logs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return ResponseEntity.ok(couponUseCases.logs(id, page, size));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CouponDtos.Response> revoke(
            @PathVariable Long id,
            @Valid @RequestBody CouponDtos.RevokeRequest request
    ) {
        return ResponseEntity.ok(couponUseCases.revoke(id, request.getStatus()));
    }
}
