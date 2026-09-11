package com.ael.algoryqrservice.coupon;

import com.ael.algoryqrservice.coupon.domain.CouponQuote;
import com.ael.algoryqrservice.model.CouponLog;
import com.ael.algoryqrservice.model.enums.CouponLogAction;
import com.ael.algoryqrservice.repository.CouponLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CouponLogWriter {

    private final CouponLogRepository couponLogRepository;

    public void write(
            Long couponId,
            Long userId,
            Long purchaseId,
            CouponLogAction action,
            CouponQuote quote,
            String message
    ) {
        couponLogRepository.save(CouponLog.builder()
                .couponId(couponId)
                .userId(userId)
                .purchaseId(purchaseId)
                .action(action)
                .listPrice(quote == null ? null : quote.listPrice())
                .discountAmount(quote == null ? null : quote.discountAmount())
                .payable(quote == null ? null : quote.payable())
                .message(message)
                .build());
    }
}
