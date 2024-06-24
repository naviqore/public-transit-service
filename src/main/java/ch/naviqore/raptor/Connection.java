package ch.naviqore.raptor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A connection is a sequence of legs to travel from an origin stop to destination stop.
 */
public interface Connection {

    LocalDateTime getDepartureTime();

    LocalDateTime getArrivalTime();

    String getFromStopId();

    String getToStopId();

    int getDurationInSeconds();

    List<Leg> getWalkTransfers();

    List<Leg> getRouteLegs();

    int getNumberOfSameStopTransfers();

    int getNumberOfTotalTransfers();

    List<Leg> getLegs();

}
