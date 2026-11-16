package com.ael.algoryqrservice.qrservice.billing.invoice;

import com.ael.algoryqrservice.qrservice.model.BillingInvoice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Default until an integrator is contracted: invoices are recorded and marked SKIPPED so the
 * ledger stays complete and they can be issued later by resetting them to PENDING.
 */
@Slf4j
@Configuration
public class EInvoiceConfiguration {

    @Bean
    @ConditionalOnMissingBean(EInvoiceProvider.class)
    public EInvoiceProvider noopEInvoiceProvider() {
        return new EInvoiceProvider() {
            @Override
            public String name() {
                return "none";
            }

            @Override
            public Result issue(BillingInvoice invoice) {
                log.info("einvoice_skipped invoiceId={} kind={} total={} reason=no_provider",
                        invoice.getId(), invoice.getKind(), invoice.getTotal());
                return Result.skipped("e-Arşiv entegratörü yapılandırılmadı");
            }
        };
    }
}
