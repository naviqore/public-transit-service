package org.naviqore.app.exception;

public class InvalidCoordinatesException extends ValidationException {
    public InvalidCoordinatesException(String message, Throwable cause) {
        super(message, cause);
    }
}
