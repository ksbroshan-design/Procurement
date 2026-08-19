package com.procurement.engine.common.exception;

public class ConstraintValidationException extends RuntimeException {
    public ConstraintValidationException(String message) {
        super(message);
    }
}
