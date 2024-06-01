package ch.naviqore.service;

import java.time.LocalDateTime;

public interface Leg {

    LegType getLegType();

    <T> T accept(LegVisitor<T> visitor);

    int getDistance();

    int getDuration();

    LocalDateTime getDepartureTime();

    LocalDateTime getArrivalTime();
}
