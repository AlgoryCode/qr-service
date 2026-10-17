package com.ael.algoryqrservice.qrservice.coupon;

import com.ael.algoryqrservice.qrservice.coupon.domain.CouponQuote;
import com.ael.algoryqrservice.qrservice.model.CouponLog;
import com.ael.algoryqrservice.qrservice.model.enums.CouponLogAction;
import com.ael.algoryqrservice.qrservice.repository.CouponLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CouponLogWriter {

    private final CouponLogRepository couponLogRepository;

    public void write(
            Long couponId,
            Long merchantId,
            Long purchaseId,
            CouponLogAction action,
            CouponQuote quote,
            String message
    ) {
        couponLogRepository.save(CouponLog.builder()
                .couponId(couponId)
                .merchantId(merchantId)
                .purchaseId(purchaseId)
                .action(action)
                .listPrice(quote == null ? null : quote.listPrice())
                .discountAmount(quote == null ? null : quote.discountAmount())
                .payable(quote == null ? null : quote.payable())
                .message(message)
                .build());
    }
}
