package org.naviqore.app.exception;

/**
 * Exception thrown when routing parameters are invalid or conflict with service capabilities.
 */
public class InvalidRoutingParametersException extends ValidationException {
    public InvalidRoutingParametersException(String message) {
        super(message);
    }

}
