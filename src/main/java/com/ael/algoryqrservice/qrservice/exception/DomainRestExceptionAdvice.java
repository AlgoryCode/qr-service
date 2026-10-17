package com.ael.algoryqrservice.qrservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Slf4j
public abstract class DomainRestExceptionAdvice {

    protected abstract String domainTag();

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        log.warn("[{}] Validation failed", domainTag());
        return ApiErrorResponseFactory.validation(ex);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(BadRequestException ex) {
        log.warn("[{}] Bad request: {}", domainTag(), ex.getMessage());
        return ApiErrorResponseFactory.messageWithCode(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NotFoundException ex) {
        log.warn("[{}] Not found: {}", domainTag(), ex.getMessage());
        return ApiErrorResponseFactory.message(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, String>> handleForbidden(ForbiddenException ex) {
        log.warn("[{}] Forbidden: {}", domainTag(), ex.getMessage());
        return ApiErrorResponseFactory.messageWithCode(HttpStatus.FORBIDDEN, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, String>> handleUnauthorized(UnauthorizedException ex) {
        log.warn("[{}] Unauthorized: {}", domainTag(), ex.getMessage());
        return ApiErrorResponseFactory.message(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflict(ConflictException ex) {
        log.warn("[{}] Conflict: {}", domainTag(), ex.getMessage());
        return ApiErrorResponseFactory.messageWithCode(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("[{}] Bad credentials", domainTag());
        return ApiErrorResponseFactory.message(HttpStatus.UNAUTHORIZED, "E-posta veya şifre hatalı");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("[{}] Data integrity violation", domainTag(), ex);
        String detail = ex.getMostSpecificCause() != null
                ? String.valueOf(ex.getMostSpecificCause().getMessage())
                : "";
        String lower = detail.toLowerCase();
        if (lower.contains("uk_merchant_staff_merchant_username")
                || lower.contains("uk_menu_waiter_username")
                || lower.contains("tbl_menu_waiter_username")) {
            return ApiErrorResponseFactory.message(
                    HttpStatus.CONFLICT,
                    "Bu kullanıcı adı bu işletmede zaten kullanılıyor"
            );
        }
        return ApiErrorResponseFactory.message(
                HttpStatus.CONFLICT,
                "Veri kaydı tamamlanamadı. Lütfen bilgilerinizi kontrol edip tekrar deneyin."
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        log.warn("[{}] Response status {}: {}", domainTag(), status.value(), message);
        return ApiErrorResponseFactory.message(status, message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneric(Exception ex) {
        log.error("[{}] Unhandled exception", domainTag(), ex);
        return ApiErrorResponseFactory.internalError();
    }
}
