package ch.naviqore.service;

import java.time.LocalDateTime;

public interface StopTime {

    Trip getTrip();

    Stop getStop();

    LocalDateTime getArrivalTime();

    LocalDateTime getDepartureTime();

}
