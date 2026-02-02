package org.naviqore.app.dto;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.naviqore.service.*;
import org.naviqore.service.SearchType;
import org.naviqore.service.StopSortStrategy;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

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

    public static Connection map(org.naviqore.service.Connection connection) {
        List<Leg> legs = connection.getLegs().stream().map(leg -> leg.accept(new LegVisitorImpl())).toList();
        return new Connection(legs);
    }

    public static List<Connection> map(List<org.naviqore.service.Connection> connections) {
        return connections.stream().map(DtoMapper::map).toList();
    }

    public static List<StopConnection> map(Map<org.naviqore.service.Stop, org.naviqore.service.Connection> connections,
                                           TimeType timeType, boolean returnConnections) {
        List<StopConnection> arrivals = new ArrayList<>();
        for (Map.Entry<org.naviqore.service.Stop, org.naviqore.service.Connection> entry : connections.entrySet()) {
            arrivals.add(new StopConnection(entry.getKey(), entry.getValue(), map(timeType), returnConnections));
        }
        return arrivals;
    }

    public static ScheduleValidity map(org.naviqore.service.Validity validity) {
        return new ScheduleValidity(validity.getStartDate(), validity.getEndDate());
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
