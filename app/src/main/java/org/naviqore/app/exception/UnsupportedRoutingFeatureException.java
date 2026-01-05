package org.naviqore.app.exception;

/**
 * Exception thrown when a routing feature is not supported by the configured router.
 */
public class UnsupportedRoutingFeatureException extends ValidationException {
    public UnsupportedRoutingFeatureException(String message) {
        super(message);
    }
}
