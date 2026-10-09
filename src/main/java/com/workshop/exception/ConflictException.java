package com.workshop.exception;

/** Maps to HTTP 409 (duplicate registration, workshop full, duplicate email...). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
