package org.naviqore.app.exception;

/**
 * Exception thrown when provided datetime is outside the schedule validity period.
 */
public class InvalidDateTimeException extends ValidationException {
    public InvalidDateTimeException(String message) {
        super(message);
    }
}
