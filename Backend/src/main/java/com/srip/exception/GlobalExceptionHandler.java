package com.srip.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Turns exceptions into consistent JSON error bodies. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiExceptions.NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ApiExceptions.NotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage(), null);
    }

    @ExceptionHandler({ApiExceptions.BadRequestException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage(), null);
    }

    @ExceptionHandler(ApiExceptions.ConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(ApiExceptions.ConflictException e) {
        return body(HttpStatus.CONFLICT, e.getMessage(), null);
    }

    @ExceptionHandler(ApiExceptions.CsvValidationException.class)
    public ResponseEntity<Map<String, Object>> handleCsv(ApiExceptions.CsvValidationException e) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage(), null);
    }

    @ExceptionHandler({ApiExceptions.AuthenticationFailedException.class, AuthenticationException.class})
    public ResponseEntity<Map<String, Object>> handleAuth(Exception e) {
        // Never echo which half of the credential pair was wrong.
        return body(HttpStatus.UNAUTHORIZED, "Invalid credentials", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException e) {
        return body(HttpStatus.FORBIDDEN, "You are not permitted to access this resource", null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .toList();
        return body(HttpStatus.BAD_REQUEST, "Request validation failed", details);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleTooLarge(MaxUploadSizeExceededException e) {
        return body(HttpStatus.PAYLOAD_TOO_LARGE,
                "The uploaded file exceeds the configured maximum size", null);
    }

    @ExceptionHandler(ApiExceptions.AiGenerationException.class)
    public ResponseEntity<Map<String, Object>> handleAi(ApiExceptions.AiGenerationException e) {
        log.error("Claude generation failed", e);
        return body(HttpStatus.BAD_GATEWAY, e.getMessage(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", null);
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message, List<String> details) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", Instant.now().toString());
        payload.put("status", status.value());
        payload.put("error", status.getReasonPhrase());
        payload.put("message", message);
        if (details != null && !details.isEmpty()) {
            payload.put("details", details);
        }
        return ResponseEntity.status(status).body(payload);
    }
}
