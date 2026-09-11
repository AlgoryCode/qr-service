package com.ael.algoryqrservice.controller;

import com.ael.algoryqrservice.coupon.CouponUseCases;
import com.ael.algoryqrservice.model.dto.CouponDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponUseCases couponUseCases;

    @GetMapping("/{code}")
    public ResponseEntity<CouponDtos.PreviewResponse> preview(@PathVariable String code) {
        return ResponseEntity.ok(couponUseCases.preview(code));
    }
}
