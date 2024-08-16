package ch.naviqore.app.dto;

import ch.naviqore.service.TimeType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * This class represents a connection between a stop and a spawn source (iso-line) in a transportation network. It
 * contains information about the stop, the leg closest to the target stop, and the connection itself.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@EqualsAndHashCode
@ToString
@Getter
public class StopConnection {

    private final Stop stop;
    private Leg connectingLeg;
    private Connection connection;

    /**
     * Constructs a new StopConnection object.
     *
     * @param serviceStop       The stop from the service.
     * @param serviceConnection The connection from the service.
     * @param timeType          The type of time (DEPARTURE or ARRIVAL), needed to construct the connecting leg.
     * @param returnConnections A boolean indicating whether to return connections and trip stop times.
     */
    public StopConnection(ch.naviqore.service.Stop serviceStop, ch.naviqore.service.Connection serviceConnection,
                          TimeType timeType, boolean returnConnections) {

        this.stop = DtoMapper.map(serviceStop);
        this.connection = DtoMapper.map(serviceConnection);
        if (timeType == TimeType.DEPARTURE) {
            prepareDepartureConnectingLeg();
        } else {
            prepareArrivalConnectingLeg();
        }
        if (!returnConnections) {
            reduceData();
        }
    }

    /**
     * Finds the index of a stop time in a trip for a given stop and time.
     *
     * @param trip     The trip to search in.
     * @param stop     The stop to find.
     * @param time     The time to match.
     * @param timeType The type of time to match (DEPARTURE or ARRIVAL).
     * @return The index of the stop time in the trip.
     */
    private static int findStopTimeIndexInTrip(Trip trip, Stop stop, LocalDateTime time, TimeType timeType) {
        List<StopTime> stopTimes = trip.getStopTimes();
        for (int i = 0; i < stopTimes.size(); i++) {
            StopTime stopTime = stopTimes.get(i);
            if (stopTime.getStop().equals(stop)) {
                if (timeType == TimeType.DEPARTURE && stopTime.getDepartureTime().equals(time)) {
                    return i;
                } else if (timeType == TimeType.ARRIVAL && stopTime.getArrivalTime().equals(time)) {
                    return i;
                }
            }
        }
        throw new IllegalStateException("Stop time not found in trip.");
    }

    /**
     * Prepares the connecting leg for a departure connection (i.e. builds a leg from the second last to the last stop
     * in the connection).
     */
    private void prepareDepartureConnectingLeg() {
        connectingLeg = this.connection.getLegs().getLast();
        if (connectingLeg.getTrip() == null) {
            return;
        }
        int stopTimeIndex = findStopTimeIndexInTrip(connectingLeg.getTrip(), connectingLeg.getToStop(),
                connectingLeg.getArrivalTime(), TimeType.ARRIVAL);
        StopTime sourceStopTime = connectingLeg.getTrip().getStopTimes().get(stopTimeIndex - 1);
        connectingLeg = new Leg(connectingLeg.getType(), sourceStopTime.getStop().getCoordinates(),
                connectingLeg.getTo(), sourceStopTime.getStop(), connectingLeg.getToStop(),
                sourceStopTime.getDepartureTime(), connectingLeg.getArrivalTime(), connectingLeg.getTrip());
    }

    /**
     * Prepares the connecting leg for an arrival connection (i.e. builds a leg from the first to the second stop in the
     * connection).
     */
    private void prepareArrivalConnectingLeg() {
        connectingLeg = this.connection.getLegs().getFirst();
        if (connectingLeg.getTrip() == null) {
            return;
        }
        int stopTimeIndex = findStopTimeIndexInTrip(connectingLeg.getTrip(), connectingLeg.getFromStop(),
                connectingLeg.getDepartureTime(), TimeType.DEPARTURE);
        StopTime targetStopTime = connectingLeg.getTrip().getStopTimes().get(stopTimeIndex + 1);
        connectingLeg = new Leg(connectingLeg.getType(), connectingLeg.getFrom(),
                targetStopTime.getStop().getCoordinates(), connectingLeg.getFromStop(), targetStopTime.getStop(),
                connectingLeg.getDepartureTime(), targetStopTime.getArrivalTime(), connectingLeg.getTrip());
    }

    /**
     * Reduces the data of the StopConnection object by setting the connection to null and nullifying the stop times in
     * the trip of the connecting leg.
     */
    private void reduceData() {
        connection = null;
        if (connectingLeg.getTrip() == null) {
            return;
        }
        Trip reducedTrip = new Trip(connectingLeg.getTrip().getHeadSign(), connectingLeg.getTrip().getRoute(), null);
        connectingLeg = new Leg(connectingLeg.getType(), connectingLeg.getFrom(), connectingLeg.getTo(),
                connectingLeg.getFromStop(), connectingLeg.getToStop(), connectingLeg.getDepartureTime(),
                connectingLeg.getArrivalTime(), reducedTrip);
    }

}

