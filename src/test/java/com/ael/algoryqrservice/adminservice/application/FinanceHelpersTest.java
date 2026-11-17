package com.ael.algoryqrservice.adminservice.application;

import com.ael.algoryqrservice.adminservice.infrastructure.persistence.entity.BillingReminder.Kind;
import com.ael.algoryqrservice.adminservice.infrastructure.persistence.repository.EmailTemplateRepository;
import com.ael.algoryqrservice.qrservice.exception.BadRequestException;
import com.ael.algoryqrservice.qrservice.model.Purchase;
import com.ael.algoryqrservice.qrservice.model.enums.BillingPeriod;
import com.ael.algoryqrservice.qrservice.model.enums.PaymentStyle;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseStatus;
import com.ael.algoryqrservice.qrservice.model.enums.PurchaseType;
import com.ael.algoryqrservice.qrservice.model.enums.SubscriptionStatus;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure rules behind reminders, e-mail templates, revenue and CSV exports. */
class FinanceHelpersTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 10, 0);

    @Test
    void dueReminder_onlyForManualPayersAtSevenAndOneDays() {
        assertThat(DunningService.dueReminder(manual(NOW.plusDays(7).minusHours(1)), NOW)).contains(Kind.DUE_IN_7);
        assertThat(DunningService.dueReminder(manual(NOW.plusDays(1)), NOW)).contains(Kind.DUE_IN_1);
        assertThat(DunningService.dueReminder(manual(NOW.plusDays(4)), NOW)).isEmpty();
        assertThat(DunningService.dueReminder(manual(NOW.plusDays(30)), NOW)).isEmpty();
        // Auto-renewing card subscription: the charge happens by itself, no "pay now" mail.
        assertThat(DunningService.dueReminder(autoRenew(NOW.plusDays(7).minusHours(1)), NOW)).isEmpty();
    }

    @Test
    void dueReminder_followsUpAWeekIntoArrears() {
        Purchase failed = autoRenew(NOW.minusDays(8));
        assertThat(DunningService.dueReminder(failed, NOW)).contains(Kind.OVERDUE_7);
        assertThat(DunningService.dueReminder(autoRenew(NOW.minusDays(3)), NOW)).isEmpty();
    }

    @Test
    void templates_fillKnownAndBlankUnknown() {
        assertThat(EmailTemplateService.fill("Merhaba {{userName}}, {{ amount }} {{nope}}!",
                Map.of("userName", "Ayşe", "amount", "1.499,00 ₺"))).isEqualTo("Merhaba Ayşe, 1.499,00 ₺ !");
        assertThat(EmailTemplateService.fill("{{payUrl}}", Map.of("payUrl", "https://x/$1"))).isEqualTo("https://x/$1");
    }

    @Test
    void templates_rejectPlaceholdersTheMailCannotFill() {
        EmailTemplateService service = new EmailTemplateService(Mockito.mock(EmailTemplateRepository.class));
        assertThatThrownBy(() -> service.save(EmailTemplateService.PAYMENT_REMINDER, "Konu {{iban}}", "Gövde", true, "a"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("iban");
        assertThatThrownBy(() -> service.save("NOPE", "a", "b", true, "a")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void monthly_spreadsRenewalOverItsInterval() {
        Purchase yearly = manual(NOW.plusMonths(6));
        yearly.setPrice(new BigDecimal("1200.00"));
        yearly.setBillingPeriod(BillingPeriod.YEARLY);
        assertThat(RevenueService.monthly(yearly)).isEqualByComparingTo("100.00");

        Purchase monthly = manual(NOW.plusDays(10));
        monthly.setBillingPeriod(BillingPeriod.MONTHLY);
        monthly.setRecurringPrice(new BigDecimal("150.00"));
        assertThat(RevenueService.monthly(monthly)).isEqualByComparingTo("150.00");
    }

    @Test
    void csvCells_quoteSeparatorsAndDefuseFormulas() {
        assertThat(AdminExportService.cell("a;b")).isEqualTo("\"a;b\"");
        assertThat(AdminExportService.cell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(AdminExportService.cell("x\"; y")).isEqualTo("\"x\"\"; y\"");
        assertThat(AdminExportService.cell("=HYPERLINK(1)")).isEqualTo("'=HYPERLINK(1)");
        assertThat(AdminExportService.cell(new BigDecimal("-12.50"))).isEqualTo("-12,50");
        assertThat(AdminExportService.cell(null)).isEmpty();
    }

    private static Purchase manual(LocalDateTime expiresAt) {
        Purchase p = autoRenew(expiresAt);
        p.setPaymentStyle(PaymentStyle.ONE_TIME);
        p.setSubscriptionId(null);
        return p;
    }

    private static Purchase autoRenew(LocalDateTime expiresAt) {
        Purchase p = new Purchase();
        p.setId(1L);
        p.setMerchantId(9L);
        p.setPurchaseType(PurchaseType.PAID);
        p.setStatus(PurchaseStatus.ACTIVE);
        p.setPaymentStyle(PaymentStyle.SUBSCRIPTION);
        p.setSubscriptionId("sub-9");
        p.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        p.setPrice(new BigDecimal("149.90"));
        p.setCurrency("TRY");
        p.setExpiresAt(expiresAt);
        return p;
    }
}
