package com.ael.algoryqrservice.customerservice.presentation.exception;

import com.ael.algoryqrservice.customerservice.presentation.controller.CustomerAccountController;
import com.ael.algoryqrservice.qrservice.exception.DomainRestExceptionAdvice;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.ael.algoryqrservice.customerservice.presentation")
public class CustomerApiExceptionHandler extends DomainRestExceptionAdvice {

    @Override
    protected String domainTag() {
        return "customer";
    }
}
