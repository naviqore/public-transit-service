package ch.naviqore.service.exception;

import java.time.LocalDate;

public class TripNotActiveException extends Exception {

    public TripNotActiveException(String id, LocalDate date) {
        super("Found trip " + id + " but is not active on " + date + ".");
    }

}
