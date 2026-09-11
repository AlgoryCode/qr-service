package com.ael.algoryqrservice.coupon;

import com.ael.algoryqrservice.coupon.domain.CouponPolicy;
import com.ael.algoryqrservice.exception.BadRequestException;
import com.ael.algoryqrservice.exception.NotFoundException;
import com.ael.algoryqrservice.model.Coupon;
import com.ael.algoryqrservice.model.CouponLog;
import com.ael.algoryqrservice.model.dto.CouponDtos;
import com.ael.algoryqrservice.model.enums.CouponLogAction;
import com.ael.algoryqrservice.model.enums.CouponStatus;
import com.ael.algoryqrservice.repository.CouponLogRepository;
import com.ael.algoryqrservice.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CouponUseCases {

    private static final int MAX_PAGE_SIZE = 50;

    private final CouponRepository couponRepository;
    private final CouponLogRepository couponLogRepository;
    private final CouponLogWriter couponLogWriter;

    @Transactional
    public CouponDtos.Response create(CouponDtos.CreateRequest request, Long adminUserId) {
        String code = CouponPolicy.normalizeCode(request.getCode());
        CouponPolicy.validateCreate(request.getDiscountType(), request.getDiscountValue(), request.getExpiresAt());
        if (couponRepository.existsByCode(code)) {
            throw new BadRequestException("Bu kupon kodu zaten var");
        }
        Coupon coupon = couponRepository.save(Coupon.builder()
                .code(code)
                .discountType(request.getDiscountType())
                .discountValue(request.getDiscountValue())
                .expiresAt(request.getExpiresAt())
                .validFrom(request.getValidFrom())
                .status(CouponStatus.UNUSED)
                .createdByAdminId(adminUserId)
                .build());
        return toResponse(coupon);
    }

    @Transactional(readOnly = true)
    public CouponDtos.PageResponse list(String query, CouponStatus status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        String trimmed = query == null || query.isBlank() ? null : query.trim();
        Page<Coupon> result = couponRepository.search(status, trimmed, pageable);
        return CouponDtos.PageResponse.builder()
                .content(result.getContent().stream().map(this::toResponse).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .hasNext(result.hasNext())
                .build();
    }

    @Transactional(readOnly = true)
    public CouponDtos.Response getById(Long id) {
        return toResponse(couponRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Kupon bulunamadi")));
    }

    @Transactional(readOnly = true)
    public CouponDtos.PreviewResponse preview(String rawCode) {
        Coupon coupon = couponRepository.findByCode(CouponPolicy.normalizeCode(rawCode))
                .orElseThrow(() -> new NotFoundException("Kupon bulunamadi"));
        return CouponDtos.PreviewResponse.builder()
                .code(coupon.getCode())
                .discountType(coupon.getDiscountType())
                .discountValue(coupon.getDiscountValue())
                .expiresAt(coupon.getExpiresAt())
                .usable(CouponPolicy.previewUsable(coupon))
                .build();
    }

    @Transactional(readOnly = true)
    public CouponDtos.LogPageResponse logs(Long couponId, int page, int size) {
        if (!couponRepository.existsById(couponId)) {
            throw new NotFoundException("Kupon bulunamadi");
        }
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<CouponLog> result = couponLogRepository.findByCouponIdOrderByCreatedAtDesc(
                couponId,
                PageRequest.of(safePage, safeSize)
        );
        return CouponDtos.LogPageResponse.builder()
                .content(result.getContent().stream().map(this::toLog).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .hasNext(result.hasNext())
                .build();
    }

    @Transactional
    public CouponDtos.Response revoke(Long id, String status) {
        if (status == null || !"REVOKED".equals(status.toUpperCase(Locale.ROOT))) {
            throw new BadRequestException("Yalnizca status=REVOKED desteklenir");
        }
        Coupon coupon = couponRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Kupon bulunamadi"));
        if (coupon.getStatus() != CouponStatus.UNUSED) {
            throw new BadRequestException("Yalnizca kullanilmamis kupon iptal edilebilir");
        }
        coupon.setStatus(CouponStatus.REVOKED);
        couponRepository.save(coupon);
        couponLogWriter.write(
                coupon.getId(),
                null,
                null,
                CouponLogAction.REVOKED,
                null,
                "Kupon admin tarafindan iptal edildi"
        );
        return toResponse(coupon);
    }

    private CouponDtos.Response toResponse(Coupon coupon) {
        return CouponDtos.Response.builder()
                .id(coupon.getId())
                .code(coupon.getCode())
                .discountType(coupon.getDiscountType())
                .discountValue(coupon.getDiscountValue())
                .expiresAt(coupon.getExpiresAt())
                .validFrom(coupon.getValidFrom())
                .status(coupon.getStatus())
                .reservedPurchaseId(coupon.getReservedPurchaseId())
                .usedPurchaseId(coupon.getUsedPurchaseId())
                .usedByUserId(coupon.getUsedByUserId())
                .usedAt(coupon.getUsedAt())
                .createdByAdminId(coupon.getCreatedByAdminId())
                .createdAt(coupon.getCreatedAt())
                .build();
    }

    private CouponDtos.LogResponse toLog(CouponLog log) {
        return CouponDtos.LogResponse.builder()
                .id(log.getId())
                .couponId(log.getCouponId())
                .userId(log.getUserId())
                .purchaseId(log.getPurchaseId())
                .action(log.getAction())
                .listPrice(log.getListPrice())
                .discountAmount(log.getDiscountAmount())
                .payable(log.getPayable())
                .message(log.getMessage())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
