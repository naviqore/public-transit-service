package ch.naviqore.service;

import java.time.LocalDateTime;

public interface StopTime {

    Stop getStop();

    LocalDateTime getArrivalTime();

    LocalDateTime getDepartureTime();

}
