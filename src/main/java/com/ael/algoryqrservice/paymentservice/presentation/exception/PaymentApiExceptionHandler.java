package com.ael.algoryqrservice.paymentservice.presentation.exception;

import com.ael.algoryqrservice.paymentservice.domain.exception.DomainException;
import com.ael.algoryqrservice.paymentservice.domain.exception.PaymentNotFoundException;
import com.ael.algoryqrservice.paymentservice.domain.exception.PaymentMethodNotFoundException;
import com.ael.algoryqrservice.paymentservice.domain.exception.PaymentProcessingException;
import com.ael.algoryqrservice.paymentservice.domain.exception.SubscriptionNotFoundException;
import com.ael.algoryqrservice.paymentservice.presentation.dto.response.ApiErrorResponse;
import com.ael.algoryqrservice.paymentservice.presentation.dto.response.FieldErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class PaymentApiExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handlePaymentNotFoundException(
            PaymentNotFoundException exception,
            HttpServletRequest request
    ) {
        log.warn(
                "Payment not found. path={} reason={}",
                request.getRequestURI(),
                exception.getErrorCode()
        );
        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                exception.getErrorCode(),
                exception.getMessage(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler({PaymentMethodNotFoundException.class, SubscriptionNotFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleOwnedResourceNotFoundException(
            DomainException exception,
            HttpServletRequest request
    ) {
        log.warn(
                "Payment resource not found. path={} reason={}",
                request.getRequestURI(),
                exception.getErrorCode()
        );
        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                exception.getErrorCode(),
                exception.getMessage(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(PaymentProcessingException.class)
    public ResponseEntity<ApiErrorResponse> handlePaymentProcessingException(
            PaymentProcessingException exception,
            HttpServletRequest request
    ) {
        log.warn(
                "Payment processing failed. path={} reason={}",
                request.getRequestURI(),
                exception.getErrorCode()
        );
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                exception.getErrorCode(),
                exception.getMessage(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiErrorResponse> handleDomainException(
            DomainException exception,
            HttpServletRequest request
    ) {
        log.warn(
                "Payment domain error. path={} reason={}",
                request.getRequestURI(),
                exception.getErrorCode()
        );
        return buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                exception.getErrorCode(),
                exception.getMessage(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<FieldErrorResponse> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldErrorResponse)
                .toList();

        log.warn(
                "Request validation failed. path={} fields={}",
                request.getRequestURI(),
                fieldErrors.stream().map(FieldErrorResponse::getField).toList()
        );

        ApiErrorResponse errorResponse = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("Validation failed")
                .errorCode("VALIDATION_ERROR")
                .path(request.getRequestURI())
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error("Unexpected error occurred", exception);
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred",
                request.getRequestURI()
        );
    }

    private FieldErrorResponse toFieldErrorResponse(FieldError fieldError) {
        return FieldErrorResponse.builder()
                .field(fieldError.getField())
                .message(fieldError.getDefaultMessage())
                .rejectedValue(isSensitiveField(fieldError.getField()) ? null : fieldError.getRejectedValue())
                .build();
    }

    private boolean isSensitiveField(String field) {
        String normalized = field.toLowerCase();
        return normalized.contains("cardnumber")
                || normalized.contains("cvc")
                || normalized.contains("expire")
                || normalized.contains("cardholder");
    }

    private ResponseEntity<ApiErrorResponse> buildErrorResponse(
            HttpStatus status,
            String errorCode,
            String message,
            String path
    ) {
        ApiErrorResponse errorResponse = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .errorCode(errorCode)
                .path(path)
                .build();

        return ResponseEntity.status(status).body(errorResponse);
    }
}
