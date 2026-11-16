package com.ael.algoryqrservice.qrservice.billing.invoice;

import com.ael.algoryqrservice.qrservice.model.BillingInvoice;

/** e-Arşiv / e-Fatura integrator (GİB özel entegratör). One implementation is active at a time. */
public interface EInvoiceProvider {

    /** Short id stored on the invoice, e.g. "none", "uyumsoft". */
    String name();

    /**
     * Issues the document. Must be idempotent on {@link BillingInvoice#getId()} (send it as the
     * integrator's local reference), because a timeout is retried.
     */
    Result issue(BillingInvoice invoice);

    record Result(Outcome outcome, String providerRef, String invoiceNumber, String documentUrl, String error) {

        public enum Outcome { ISSUED, SKIPPED, FAILED }

        public static Result issued(String providerRef, String invoiceNumber, String documentUrl) {
            return new Result(Outcome.ISSUED, providerRef, invoiceNumber, documentUrl, null);
        }

        public static Result skipped(String reason) {
            return new Result(Outcome.SKIPPED, null, null, null, reason);
        }

        public static Result failed(String error) {
            return new Result(Outcome.FAILED, null, null, null, error);
        }
    }
}
