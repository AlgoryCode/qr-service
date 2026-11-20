package com.ael.algoryqrservice.qrservice.console.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * What a session costs at the end once everything the customer can use is taken off. Deductions apply in this
 * order, each on what is left: membership minutes, loyalty minutes (both worth their share of the time amount),
 * level discount (a percent of the time amount left; not on package sessions), bonus balance, manual discount.
 */
public final class DeviceCheckout {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private DeviceCheckout() {
    }

    /** What the customer has; null amounts and percents count as none. */
    public record Customer(int membershipMinutes, int loyaltyMinutes, BigDecimal tierPercent, BigDecimal bonusBalance) {

        public static final Customer NONE = new Customer(0, 0, null, null);
    }

    /** What the staff chose; give at most one of the manual discounts. */
    public record Options(
            boolean useMembership,
            boolean useLoyalty,
            boolean useBonus,
            BigDecimal discountAmount,
            BigDecimal discountPercent
    ) {

        public static final Options NONE = new Options(false, false, false, null, null);
    }

    public record Result(
            BigDecimal grossAmount,
            int membershipMinutesUsed,
            BigDecimal membershipValue,
            int loyaltyMinutesUsed,
            BigDecimal loyaltyValue,
            BigDecimal tierDiscount,
            BigDecimal bonusUsed,
            BigDecimal manualDiscount,
            BigDecimal totalDiscount,
            BigDecimal amount
    ) {
    }

    public static Result compute(
            BigDecimal timeAmount,
            int billedMinutes,
            BigDecimal itemsAmount,
            boolean packaged,
            Customer customer,
            Options options
    ) {
        Customer c = customer == null ? Customer.NONE : customer;
        Options o = options == null ? Options.NONE : options;
        BigDecimal time = nz(timeAmount);
        BigDecimal gross = time.add(nz(itemsAmount)).setScale(2, RoundingMode.HALF_UP);

        int membershipUsed = o.useMembership() ? Math.max(Math.min(c.membershipMinutes(), billedMinutes), 0) : 0;
        BigDecimal membershipValue = DeviceRentalPricing.valueOfMinutes(time, billedMinutes, membershipUsed);

        int loyaltyUsed = o.useLoyalty() ? Math.max(Math.min(c.loyaltyMinutes(), billedMinutes - membershipUsed), 0) : 0;
        BigDecimal loyaltyValue = DeviceRentalPricing.valueOfMinutes(time, billedMinutes, loyaltyUsed)
                .min(time.subtract(membershipValue).max(BigDecimal.ZERO))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal timeLeft = time.subtract(membershipValue).subtract(loyaltyValue).max(BigDecimal.ZERO);
        BigDecimal tierDiscount = ZERO;
        if (!packaged && c.tierPercent() != null && c.tierPercent().signum() > 0) {
            tierDiscount = timeLeft.multiply(c.tierPercent()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        }

        BigDecimal left = gross.subtract(membershipValue).subtract(loyaltyValue).subtract(tierDiscount).max(BigDecimal.ZERO);
        BigDecimal bonusUsed = ZERO;
        if (o.useBonus() && c.bonusBalance() != null && c.bonusBalance().signum() > 0) {
            bonusUsed = c.bonusBalance().min(left).setScale(2, RoundingMode.HALF_UP);
        }
        left = left.subtract(bonusUsed);

        BigDecimal manual = ZERO;
        if (left.signum() > 0) {
            if (o.discountAmount() != null) {
                manual = o.discountAmount();
            } else if (o.discountPercent() != null) {
                manual = left.multiply(o.discountPercent()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            }
            manual = manual.min(left).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal total = membershipValue.add(loyaltyValue).add(tierDiscount).add(bonusUsed).add(manual)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal amount = gross.subtract(total).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        return new Result(gross, membershipUsed, membershipValue, loyaltyUsed, loyaltyValue, tierDiscount, bonusUsed,
                manual, total, amount);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
