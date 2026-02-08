package org.naviqore.raptor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * A connection is a sequence of legs to travel from an origin stop to destination stop.
 */
public interface Connection {

    OffsetDateTime getDepartureTime();

    OffsetDateTime getArrivalTime();

    String getFromStopId();

    String getToStopId();

    int getDurationInSeconds();

    List<Leg> getWalkTransfers();

    List<Leg> getRouteLegs();

    int getNumberOfSameStopTransfers();

    int getNumberOfTotalTransfers();

    List<Leg> getLegs();

}
