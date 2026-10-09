package com.workshop.exception;

/** Maps to HTTP 400 for invalid input that Bean Validation cannot express. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
