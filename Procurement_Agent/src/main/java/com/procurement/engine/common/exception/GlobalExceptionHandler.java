package com.procurement.engine.common.exception;

import com.procurement.engine.common.model.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidStateTransition(InvalidStateTransitionException ex, HttpServletRequest request) {
        log.warn("Invalid state transition: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION", ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(ConstraintValidationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintValidation(ConstraintValidationException ex, HttpServletRequest request) {
        log.warn("Constraint validation failed: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, "CONSTRAINT_VALIDATION_FAILED", ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<ErrorResponse> handleAuthorizationException(AuthorizationException ex, HttpServletRequest request) {
        log.warn("Authorization exception: {}", ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "AUTHORIZATION_DENIED", ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(ApprovalException.class)
    public ResponseEntity<ErrorResponse> handleApprovalException(ApprovalException ex, HttpServletRequest request) {
        log.warn("Approval exception: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "APPROVAL_ERROR", ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(RevalidationException.class)
    public ResponseEntity<ErrorResponse> handleRevalidationException(RevalidationException ex, HttpServletRequest request) {
        log.warn("Revalidation exception: {}", ex.getMessage());
        return buildResponse(HttpStatus.PRECONDITION_FAILED, "REVALIDATION_FAILED", ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(PurchaseException.class)
    public ResponseEntity<ErrorResponse> handlePurchaseException(PurchaseException ex, HttpServletRequest request) {
        log.warn("Purchase exception: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "PURCHASE_FAILED", ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidRequestException ex, HttpServletRequest request) {
        log.warn("Invalid request: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, Object> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        log.warn("Validation error on {}: {}", request.getRequestURI(), errors);
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid request body fields", request.getRequestURI(), errors);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        log.warn("Bad credentials on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid email or password", request.getRequestURI(), null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied on {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access is denied", request.getRequestURI(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}: ", request.getRequestURI(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", ex.getMessage(), request.getRequestURI(), null);
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String error, String message, String path, Map<String, Object> details) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(error)
                .message(message)
                .path(path)
                .details(details)
                .build();
        return ResponseEntity.status(status).body(response);
    }
}
