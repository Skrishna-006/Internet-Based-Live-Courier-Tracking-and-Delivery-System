package com.courier.tracking.exception;

/**
 * Domain-specific exception thrown when an incorrect delivery OTP is submitted during delivery verification.
 * Extends IllegalArgumentException so GlobalExceptionHandler translates it to HTTP 400 Bad Request.
 * Narrowly scoped to allow @Transactional(noRollbackFor = InvalidDeliveryOtpException.class) on delivery
 * confirmation, ensuring the incremented failed-attempt count and lockout state are committed,
 * while guaranteeing that all unrelated IllegalArgumentExceptions, IllegalStateExceptions, and data errors
 * trigger standard transaction rollback and prevent partial state commits.
 */
public class InvalidDeliveryOtpException extends IllegalArgumentException {

    public InvalidDeliveryOtpException(String message) {
        super(message);
    }
}
