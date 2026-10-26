package com.courier.tracking.exception;

/**
 * Thrown when attempting to create a resource that violates unique business identity
 * (e.g., duplicate email address).
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
