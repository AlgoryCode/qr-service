package com.ael.algoryqrservice.fulfillmentservice.presentation.exception;

import com.ael.algoryqrservice.fulfillmentservice.domain.exception.DomainException;
import com.ael.algoryqrservice.fulfillmentservice.domain.exception.InvalidPaymentEventException;
import com.ael.algoryqrservice.fulfillmentservice.domain.exception.PackageAlreadyAssignedException;
import com.ael.algoryqrservice.fulfillmentservice.domain.exception.QuotaExceededException;
import com.ael.algoryqrservice.fulfillmentservice.domain.exception.ResourceNotFoundException;
import com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response.ApiErrorResponse;
import com.ael.algoryqrservice.fulfillmentservice.presentation.dto.response.FieldErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import com.ael.algoryqrservice.fulfillmentservice.presentation.controller.FulfillmentController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestControllerAdvice(basePackageClasses = FulfillmentController.class)
public class FulfillmentApiExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            ResourceNotFoundException exception,
            HttpServletRequest request
    ) {
        return build(HttpStatus.NOT_FOUND, exception.getErrorCode(), exception.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(InvalidPaymentEventException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidEvent(
            InvalidPaymentEventException exception,
            HttpServletRequest request
    ) {
        return build(HttpStatus.BAD_REQUEST, exception.getErrorCode(), exception.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(PackageAlreadyAssignedException.class)
    public ResponseEntity<ApiErrorResponse> handlePackageExists(
            PackageAlreadyAssignedException exception,
            HttpServletRequest request
    ) {
        return build(HttpStatus.CONFLICT, exception.getErrorCode(), exception.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(QuotaExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleQuota(
            QuotaExceededException exception,
            HttpServletRequest request
    ) {
        return build(HttpStatus.CONFLICT, exception.getErrorCode(), exception.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiErrorResponse> handleDomain(
            DomainException exception,
            HttpServletRequest request
    ) {
        return build(HttpStatus.BAD_REQUEST, exception.getErrorCode(), exception.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<FieldErrorResponse> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        return build(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Validation failed",
                request.getRequestURI(),
                fieldErrors
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingResource(HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found", request.getRequestURI(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error("Unexpected error. path={}", request.getRequestURI(), exception);
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Unexpected server error",
                request.getRequestURI(),
                null
        );
    }

    private FieldErrorResponse toFieldError(FieldError fieldError) {
        return new FieldErrorResponse(fieldError.getField(), fieldError.getDefaultMessage());
    }

    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status,
            String errorCode,
            String message,
            String path,
            List<FieldErrorResponse> fieldErrors
    ) {
        return ResponseEntity.status(status).body(
                new ApiErrorResponse(
                        LocalDateTime.now(),
                        status.value(),
                        status.getReasonPhrase(),
                        message,
                        errorCode,
                        path,
                        fieldErrors
                )
        );
    }
}
