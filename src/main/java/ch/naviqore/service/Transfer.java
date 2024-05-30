package ch.naviqore.service;

import java.time.LocalDateTime;

public interface Transfer extends Leg {

    LocalDateTime getArrivalTime();

    LocalDateTime getDepartureTime();

    Stop getSourceStop();

    Stop getTargetStop();

}
