package com.ael.algoryqrservice.model.dto;

import com.ael.algoryqrservice.model.enums.BillingPeriod;
import com.ael.algoryqrservice.model.enums.PlanChangeTiming;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PlanChangeRequest {

    @NotNull(message = "Hedef paket id zorunludur")
    private Long toPackageId;

    @NotNull(message = "Gecis zamani zorunludur")
    private PlanChangeTiming timing;

    private BillingPeriod billingPeriod;

    private Long paymentMethodId;

    @NotNull(message = "Hak devri uyarisi onayi zorunludur")
    private Boolean warningAck;

    @AssertTrue(message = "Onceki paketten hak devri olmadigi onaylanmalidir")
    public boolean isWarningAcknowledged() {
        return Boolean.TRUE.equals(warningAck);
    }
}
