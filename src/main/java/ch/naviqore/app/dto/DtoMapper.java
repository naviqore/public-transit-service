package ch.naviqore.app.dto;

import ch.naviqore.service.SearchType;
import ch.naviqore.service.TimeType;
import ch.naviqore.service.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DtoMapper {

    public static Stop map(ch.naviqore.service.Stop stop) {
        return new Stop(stop.getId(), stop.getName(), stop.getLocation());
    }

    public static DistanceToStop map(ch.naviqore.service.Stop stop, double latitude, double longitude) {
        return new DistanceToStop(map(stop), stop.getLocation().distanceTo(latitude, longitude));
    }

    public static Departure map(ch.naviqore.service.StopTime stopTime) {
        return new Departure(
                new StopTime(map(stopTime.getStop()), stopTime.getArrivalTime(), stopTime.getDepartureTime()),
                map(stopTime.getTrip()));
    }

    public static Trip map(ch.naviqore.service.Trip trip) {
        List<StopTime> stopTimes = trip.getStopTimes()
                .stream()
                .map(stopTime -> new StopTime(map(stopTime.getStop()), stopTime.getArrivalTime(),
                        stopTime.getDepartureTime()))
                .toList();

        return new Trip(trip.getHeadSign(), map(trip.getRoute()), stopTimes);
    }

    public static Route map(ch.naviqore.service.Route route) {
        return new Route(route.getId(), route.getName(), route.getShortName(), route.getRouteType());
    }

    public static SearchType map(ch.naviqore.app.dto.SearchType searchType) {
        return switch (searchType) {
            case STARTS_WITH -> SearchType.STARTS_WITH;
            case ENDS_WITH -> SearchType.ENDS_WITH;
            case CONTAINS -> SearchType.CONTAINS;
            case EXACT -> SearchType.EXACT;
        };
    }

    public static TimeType map(ch.naviqore.app.dto.TimeType timeType) {
        return switch (timeType) {
            case DEPARTURE -> TimeType.DEPARTURE;
            case ARRIVAL -> TimeType.ARRIVAL;
        };
    }

    public static Connection map(ch.naviqore.service.Connection connection) {
        List<Leg> legs = connection.getLegs().stream().map(leg -> leg.accept(new LegVisitorImpl())).toList();
        return new Connection(legs);
    }

    public static StopConnection map(ch.naviqore.service.Stop stop, ch.naviqore.service.Connection serviceConnection,
                                     TimeType timeType, boolean returnConnections) {

        Connection connection = map(serviceConnection);
        Leg connectingLeg;

        if (timeType == TimeType.DEPARTURE) {
            connectingLeg = connection.getLegs().getLast();
            // create a leg from stop before arrival stop (on trip) to the arrival stop
            if (connectingLeg.getTrip() != null) {
                int stopTimeIndex = findStopTimeIndexInTrip(connectingLeg.getTrip(), connectingLeg.getToStop(),
                        connectingLeg.getArrivalTime(), TimeType.ARRIVAL);
                StopTime sourceStopTime = connectingLeg.getTrip().getStopTimes().get(stopTimeIndex - 1);
                connectingLeg = new Leg(connectingLeg.getType(), sourceStopTime.getStop().getCoordinates(),
                        connectingLeg.getTo(), sourceStopTime.getStop(), connectingLeg.getToStop(),
                        sourceStopTime.getDepartureTime(), connectingLeg.getArrivalTime(), connectingLeg.getTrip());
            }
        } else {
            connectingLeg = connection.getLegs().getFirst();
            // create a leg from the departure stop to the first stop on the trip
            if (connectingLeg.getTrip() != null) {
                int stopTimeIndex = findStopTimeIndexInTrip(connectingLeg.getTrip(), connectingLeg.getFromStop(),
                        connectingLeg.getDepartureTime(), TimeType.DEPARTURE);
                StopTime targetStopTime = connectingLeg.getTrip().getStopTimes().get(stopTimeIndex + 1);
                connectingLeg = new Leg(connectingLeg.getType(), connectingLeg.getFrom(),
                        targetStopTime.getStop().getCoordinates(), connectingLeg.getFromStop(),
                        targetStopTime.getStop(), connectingLeg.getDepartureTime(), targetStopTime.getArrivalTime(),
                        connectingLeg.getTrip());
            }
        }

        if( connectingLeg.getTrip() != null && ! returnConnections ) {
            // nullify stop times from trips if connections are not requested (reducing payload)
            Trip reducedTrip = new Trip(connectingLeg.getTrip().getHeadSign(), connectingLeg.getTrip().getRoute(),
                    null);
            connectingLeg = new Leg(connectingLeg.getType(), connectingLeg.getFrom(), connectingLeg.getTo(),
                    connectingLeg.getFromStop(), connectingLeg.getToStop(), connectingLeg.getDepartureTime(),
                    connectingLeg.getArrivalTime(), reducedTrip);
        }

        return new StopConnection(map(stop), connectingLeg, returnConnections ? connection : null);
    }

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

    private static class LegVisitorImpl implements LegVisitor<Leg> {
        @Override
        public Leg visit(PublicTransitLeg publicTransitLeg) {
            return new Leg(LegType.ROUTE, publicTransitLeg.getDeparture().getStop().getLocation(),
                    publicTransitLeg.getArrival().getStop().getLocation(),
                    map(publicTransitLeg.getDeparture().getStop()), map(publicTransitLeg.getArrival().getStop()),
                    publicTransitLeg.getDeparture().getDepartureTime(), publicTransitLeg.getArrival().getArrivalTime(),
                    map(publicTransitLeg.getTrip()));
        }

        @Override
        public Leg visit(Transfer transfer) {
            return new Leg(LegType.WALK, transfer.getSourceStop().getLocation(), transfer.getTargetStop().getLocation(),
                    map(transfer.getSourceStop()), map(transfer.getTargetStop()), transfer.getDepartureTime(),
                    transfer.getArrivalTime(), null);
        }

        @Override
        public Leg visit(Walk walk) {
            Stop sourceStop = null;
            Stop targetStop = null;

            // set stop depending on walk type
            if (walk.getStop().isPresent()) {
                switch (walk.getWalkType()) {
                    case WalkType.LAST_MILE -> sourceStop = map(walk.getStop().get());
                    case WalkType.FIRST_MILE -> targetStop = map(walk.getStop().get());
                    // a walk between two stops is a TransferLeg and not a Walk, therefore this case should not occur
                    case DIRECT -> throw new IllegalStateException(
                            "No stop should be present in a direct walk between two location.");
                }
            }

            return new Leg(LegType.WALK, walk.getSourceLocation(), walk.getTargetLocation(), sourceStop, targetStop,
                    walk.getDepartureTime(), walk.getArrivalTime(), null);
        }
    }

}
