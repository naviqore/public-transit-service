package org.naviqore.service;

import java.time.OffsetDateTime;

public interface StopTime {

    Trip getTrip();

    Stop getStop();

    OffsetDateTime getArrivalTime();

    OffsetDateTime getDepartureTime();

}
