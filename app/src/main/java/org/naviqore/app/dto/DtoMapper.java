package org.naviqore.app.dto;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.naviqore.service.*;
import org.naviqore.service.SearchType;
import org.naviqore.service.StopSortStrategy;

import java.time.OffsetDateTime;
import java.util.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DtoMapper {

    public static Stop map(org.naviqore.service.Stop stop) {
        return new Stop(stop.getId(), stop.getName(), stop.getCoordinate());
    }

    public static DistanceToStop map(org.naviqore.service.Stop stop, double latitude, double longitude) {
        return new DistanceToStop(map(stop), stop.getCoordinate().distanceTo(latitude, longitude));
    }

    public static StopEvent map(org.naviqore.service.StopTime stopTime) {
        return new StopEvent(
                new StopTime(map(stopTime.getStop()), stopTime.getArrivalTime(), stopTime.getDepartureTime()),
                map(stopTime.getTrip()));
    }

    public static Trip map(org.naviqore.service.Trip trip) {
        List<StopTime> stopTimes = trip.getStopTimes()
                .stream()
                .map(stopTime -> new StopTime(map(stopTime.getStop()), stopTime.getArrivalTime(),
                        stopTime.getDepartureTime()))
                .toList();

        return new Trip(trip.getHeadSign(), map(trip.getRoute()), stopTimes, trip.isBikesAllowed(),
                trip.isWheelchairAccessible());
    }

    public static Route map(org.naviqore.service.Route route) {
        return new Route(route.getId(), route.getName(), route.getShortName(), map(route.getRouteType()),
                route.getRouteTypeDescription());
    }

    public static TravelMode map(org.naviqore.service.TravelMode travelMode) {
        return TravelMode.valueOf(travelMode.name());
    }

    public static org.naviqore.service.TravelMode map(TravelMode travelMode) {
        return org.naviqore.service.TravelMode.valueOf(travelMode.name());
    }

    public static EnumSet<org.naviqore.service.TravelMode> map(EnumSet<TravelMode> travelModes) {
        EnumSet<org.naviqore.service.TravelMode> serviceTravelModes = EnumSet.noneOf(
                org.naviqore.service.TravelMode.class);
        for (TravelMode travelMode : travelModes) {
            serviceTravelModes.add(map(travelMode));
        }
        return serviceTravelModes;
    }

    public static SearchType map(org.naviqore.app.dto.SearchType searchType) {
        return SearchType.valueOf(searchType.name());
    }

    public static StopSortStrategy map(org.naviqore.app.dto.StopSortStrategy stopSortStrategy) {
        return StopSortStrategy.valueOf(stopSortStrategy.name());
    }

    public static org.naviqore.service.TimeType map(TimeType timeType) {
        return switch (timeType) {
            case ARRIVAL -> org.naviqore.service.TimeType.ARRIVAL;
            case DEPARTURE -> org.naviqore.service.TimeType.DEPARTURE;
        };
    }

    public static org.naviqore.service.StopScope map(StopScope scope) {
        return org.naviqore.service.StopScope.valueOf(scope.name());
    }

    public static Leg map(org.naviqore.service.Leg leg) {
        return leg.accept(new LegVisitorImpl());
    }

    public static Connection map(org.naviqore.service.Connection connection) {
        List<Leg> legs = connection.getLegs().stream().map(DtoMapper::map).toList();
        return new Connection(legs);
    }

    public static List<Connection> map(List<org.naviqore.service.Connection> connections) {
        return connections.stream().map(DtoMapper::map).toList();
    }

    public static StopConnection map(org.naviqore.service.Stop serviceStop,
                                     org.naviqore.service.Connection serviceConnection,
                                     org.naviqore.service.TimeType timeType, boolean detailed) {

        Stop stop = DtoMapper.map(serviceStop);
        OffsetDateTime departureTime = serviceConnection.getDepartureTime();
        OffsetDateTime arrivalTime = serviceConnection.getArrivalTime();

        int transfers = Math.max(0, (int) serviceConnection.getLegs()
                .stream()
                .filter(leg -> leg.getLegType() == org.naviqore.service.LegType.PUBLIC_TRANSIT)
                .count() - 1);

        Leg leg = switch (timeType) {
            case DEPARTURE -> prepareDepartureConnectingLeg(serviceConnection);
            case ARRIVAL -> prepareArrivalConnectingLeg(serviceConnection);
        };

        if (detailed) {
            return new StopConnection(stop, departureTime, arrivalTime, transfers, leg,
                    DtoMapper.map(serviceConnection));
        } else {
            return new StopConnection(stop, departureTime, arrivalTime, transfers, stripStopTimes(leg), null);
        }
    }

    public static List<StopConnection> map(Map<org.naviqore.service.Stop, org.naviqore.service.Connection> connections,
                                           TimeType timeType, boolean detailed) {
        List<StopConnection> arrivals = new ArrayList<>();
        for (Map.Entry<org.naviqore.service.Stop, org.naviqore.service.Connection> entry : connections.entrySet()) {
            arrivals.add(map(entry.getKey(), entry.getValue(), map(timeType), detailed));
        }

        return arrivals;
    }

    public static ScheduleValidity map(org.naviqore.service.Validity validity) {
        return new ScheduleValidity(validity.getStartDate(), validity.getEndDate());
    }

    /**
     * Prepares the connecting leg for a departure connection (i.e. builds a leg from the second last to the last stop
     * in the connection).
     */
    private static Leg prepareDepartureConnectingLeg(org.naviqore.service.Connection serviceConnection) {
        Leg legDto = DtoMapper.map(serviceConnection.getLegs().getLast());

        if (legDto.getTrip() == null) {
            return legDto;
        }

        int stopTimeIndex = findStopTimeIndexInTrip(legDto.getTrip(), legDto.getToStop(), legDto.getArrivalTime(),
                org.naviqore.service.TimeType.ARRIVAL);

        assert legDto.getTrip().getStopTimes() != null;
        StopTime sourceStopTime = legDto.getTrip().getStopTimes().get(stopTimeIndex - 1);

        return new Leg(legDto.getType(), sourceStopTime.getStop().getCoordinates(), legDto.getTo(),
                sourceStopTime.getStop(), legDto.getToStop(), sourceStopTime.getDepartureTime(),
                legDto.getArrivalTime(), legDto.getTrip());
    }

    /**
     * Prepares the connecting leg for an arrival connection (i.e. builds a leg from the first to the second stop in the
     * connection).
     */
    private static Leg prepareArrivalConnectingLeg(org.naviqore.service.Connection serviceConnection) {
        Leg legDto = DtoMapper.map(serviceConnection.getLegs().getFirst());

        if (legDto.getTrip() == null) {
            return legDto;
        }

        int stopTimeIndex = findStopTimeIndexInTrip(legDto.getTrip(), legDto.getFromStop(), legDto.getDepartureTime(),
                org.naviqore.service.TimeType.DEPARTURE);

        assert legDto.getTrip().getStopTimes() != null;
        StopTime targetStopTime = legDto.getTrip().getStopTimes().get(stopTimeIndex + 1);

        return new Leg(legDto.getType(), legDto.getFrom(), targetStopTime.getStop().getCoordinates(),
                legDto.getFromStop(), targetStopTime.getStop(), legDto.getDepartureTime(),
                targetStopTime.getArrivalTime(), legDto.getTrip());
    }

    /**
     * Reduces the size of a leg by removing the full list of stop times from its associated trip.
     */
    private static Leg stripStopTimes(Leg leg) {
        if (leg.getTrip() == null) {
            return leg;
        }

        Trip trip = leg.getTrip();
        Trip reducedTrip = new Trip(trip.getHeadSign(), trip.getRoute(), null, trip.isBikesAllowed(),
                trip.isWheelchairAccessible());

        return new Leg(leg.getType(), leg.getFrom(), leg.getTo(), leg.getFromStop(), leg.getToStop(),
                leg.getDepartureTime(), leg.getArrivalTime(), reducedTrip);
    }

    /**
     * Finds the index of a stop time within a trip matching a specific stop and time.
     *
     * @param trip     The trip to search in.
     * @param stop     The stop to find.
     * @param time     The time to match.
     * @param timeType The type of time to match (DEPARTURE or ARRIVAL).
     * @return The index of the stop time in the trip.
     */
    private static int findStopTimeIndexInTrip(Trip trip, Stop stop, OffsetDateTime time,
                                               org.naviqore.service.TimeType timeType) {
        List<StopTime> stopTimes = trip.getStopTimes();
        for (int i = 0; i < Objects.requireNonNull(stopTimes).size(); i++) {
            StopTime stopTime = stopTimes.get(i);
            if (stopTime.getStop().equals(stop)) {
                if (timeType == org.naviqore.service.TimeType.DEPARTURE && stopTime.getDepartureTime().equals(time)) {
                    return i;
                } else if (timeType == org.naviqore.service.TimeType.ARRIVAL && stopTime.getArrivalTime()
                        .equals(time)) {
                    return i;
                }
            }
        }

        throw new IllegalStateException("Stop time not found in trip.");
    }

    private static class LegVisitorImpl implements LegVisitor<Leg> {
        @Override
        public Leg visit(PublicTransitLeg publicTransitLeg) {
            return new Leg(LegType.ROUTE, publicTransitLeg.getDeparture().getStop().getCoordinate(),
                    publicTransitLeg.getArrival().getStop().getCoordinate(),
                    map(publicTransitLeg.getDeparture().getStop()), map(publicTransitLeg.getArrival().getStop()),
                    publicTransitLeg.getDeparture().getDepartureTime(), publicTransitLeg.getArrival().getArrivalTime(),
                    map(publicTransitLeg.getTrip()));
        }

        @Override
        public Leg visit(Transfer transfer) {
            return new Leg(LegType.WALK, transfer.getSourceStop().getCoordinate(),
                    transfer.getTargetStop().getCoordinate(), map(transfer.getSourceStop()),
                    map(transfer.getTargetStop()), transfer.getDepartureTime(), transfer.getArrivalTime(), null);
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
