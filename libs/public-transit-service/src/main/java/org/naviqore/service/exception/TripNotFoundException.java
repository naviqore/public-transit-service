package org.naviqore.service.exception;

public class TripNotFoundException extends NotFoundException {

    public TripNotFoundException(String id) {
        super("Trip", id);
    }

}
