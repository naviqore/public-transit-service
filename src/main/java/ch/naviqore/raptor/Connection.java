package ch.naviqore.raptor;

import java.util.List;

/**
 * A connection is a sequence of legs to travel from an origin stop to destination stop.
 */
public interface Connection {

    int getDepartureTime();

    int getArrivalTime();

    String getFromStopId();

    String getToStopId();

    int getDuration();

    List<Leg> getWalkTransfers();

    List<Leg> getRouteLegs();

    int getNumberOfSameStopTransfers();

    int getNumberOfTotalTransfers();

    List<Leg> getLegs();

}
