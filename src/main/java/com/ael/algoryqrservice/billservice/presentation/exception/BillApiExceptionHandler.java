package com.ael.algoryqrservice.billservice.presentation.exception;

import com.ael.algoryqrservice.qrservice.exception.DomainRestExceptionAdvice;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.ael.algoryqrservice.billservice.presentation")
public class BillApiExceptionHandler extends DomainRestExceptionAdvice {

    @Override
    protected String domainTag() {
        return "bill";
    }
}
