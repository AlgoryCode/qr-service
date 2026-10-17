package com.ael.algoryqrservice.merchantservice.presentation.exception;

import com.ael.algoryqrservice.qrservice.exception.DomainRestExceptionAdvice;
import com.ael.algoryqrservice.merchantservice.internal.controller.InternalMerchantController;
import com.ael.algoryqrservice.merchantservice.presentation.controller.MerchantMenuCustomerController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = {
        "com.ael.algoryqrservice.merchantservice.presentation",
        "com.ael.algoryqrservice.merchantservice.internal"
})
public class MerchantApiExceptionHandler extends DomainRestExceptionAdvice {

    @Override
    protected String domainTag() {
        return "merchant";
    }
}
