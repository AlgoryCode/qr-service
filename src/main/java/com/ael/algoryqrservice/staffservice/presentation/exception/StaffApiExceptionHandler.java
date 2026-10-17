package com.ael.algoryqrservice.staffservice.presentation.exception;

import com.ael.algoryqrservice.qrservice.exception.DomainRestExceptionAdvice;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = {
        "com.ael.algoryqrservice.staffservice.presentation",
        "com.ael.algoryqrservice.staffservice.internal"
})
public class StaffApiExceptionHandler extends DomainRestExceptionAdvice {

    @Override
    protected String domainTag() {
        return "staff";
    }
}
