package com.ael.algoryqrservice.adminservice.presentation.exception;

import com.ael.algoryqrservice.qrservice.exception.DomainRestExceptionAdvice;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.ael.algoryqrservice.adminservice.presentation")
public class AdminApiExceptionHandler extends DomainRestExceptionAdvice {

    @Override
    protected String domainTag() {
        return "admin";
    }
}
