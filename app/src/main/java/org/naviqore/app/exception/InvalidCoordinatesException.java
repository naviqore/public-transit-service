package org.naviqore.app.exception;

/**
 * Exception thrown when invalid coordinates are provided.
 */
public class InvalidCoordinatesException extends ValidationException {
    public InvalidCoordinatesException(String message, Throwable cause) {
        super(message, cause);
    }
}
