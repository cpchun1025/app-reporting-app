package com.tradingreporting.api.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.StaleObjectStateException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Renders every error as {@code {"detail": ...}}, matching the FastAPI backend's error envelope. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApiException(ApiException error) {
        HttpHeaders headers = new HttpHeaders();
        if (error.getStatus() == HttpStatus.UNAUTHORIZED) {
            headers.add(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        return ResponseEntity.status(error.getStatus()).headers(headers).body(detail(error.getMessage()));
    }

    @ExceptionHandler({OptimisticLockException.class, OptimisticLockingFailureException.class, StaleObjectStateException.class})
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(Exception error) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(detail("The record was updated by another request. Reload and try again."));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(EntityNotFoundException error) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(detail(error.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException error) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(detail("Access is denied."));
    }

    @ExceptionHandler(InsufficientAuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthenticated(InsufficientAuthenticationException error) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(detail("Bearer token is required."));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class,
            jakarta.validation.ConstraintViolationException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> handleValidation(Exception error) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(detail(List.of(error.getMessage())));
    }

    private static Map<String, Object> detail(Object detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("detail", detail);
        return body;
    }
}
