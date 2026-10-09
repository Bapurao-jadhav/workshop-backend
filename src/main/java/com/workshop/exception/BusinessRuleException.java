package com.workshop.exception;

/** Maps to HTTP 422: the request is well-formed but violates a business rule. */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
