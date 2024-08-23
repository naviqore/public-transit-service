package ch.naviqore.app.dto;

import ch.naviqore.service.SearchType;
import ch.naviqore.service.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

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

        return new Trip(trip.getHeadSign(), map(trip.getRoute()), stopTimes, trip.isBikesAllowed(),
                trip.isWheelchairAccessible());
    }

    public static Route map(ch.naviqore.service.Route route) {
        return new Route(route.getId(), route.getName(), route.getShortName(), map(route.getRouteType()),
                route.getRouteTypeDescription());
    }

    public static TravelMode map(ch.naviqore.service.TravelMode travelMode) {
        return TravelMode.valueOf(travelMode.name());
    }

    public static ch.naviqore.service.TravelMode map(TravelMode travelMode) {
        return ch.naviqore.service.TravelMode.valueOf(travelMode.name());
    }

    public static EnumSet<ch.naviqore.service.TravelMode> map(EnumSet<TravelMode> travelModes) {
        EnumSet<ch.naviqore.service.TravelMode> serviceTravelModes = EnumSet.noneOf(
                ch.naviqore.service.TravelMode.class);
        for (TravelMode travelMode : travelModes) {
            serviceTravelModes.add(map(travelMode));
        }
        return serviceTravelModes;
    }

    public static SearchType map(ch.naviqore.app.dto.SearchType searchType) {
        return SearchType.valueOf(searchType.name());
    }

    public static ch.naviqore.service.TimeType map(TimeType timeType) {
        return switch (timeType) {
            case ARRIVAL -> ch.naviqore.service.TimeType.ARRIVAL;
            case DEPARTURE -> ch.naviqore.service.TimeType.DEPARTURE;
        };
    }

    public static Connection map(ch.naviqore.service.Connection connection) {
        List<Leg> legs = connection.getLegs().stream().map(leg -> leg.accept(new LegVisitorImpl())).toList();
        return new Connection(legs);
    }

    public static List<Connection> map(List<ch.naviqore.service.Connection> connections) {
        return connections.stream().map(DtoMapper::map).toList();
    }

    public static List<StopConnection> map(Map<ch.naviqore.service.Stop, ch.naviqore.service.Connection> connections,
                                           TimeType timeType, boolean returnConnections) {
        List<StopConnection> arrivals = new ArrayList<>();
        for (Map.Entry<ch.naviqore.service.Stop, ch.naviqore.service.Connection> entry : connections.entrySet()) {
            arrivals.add(new StopConnection(entry.getKey(), entry.getValue(), map(timeType), returnConnections));
        }
        return arrivals;
    }

    public static ScheduleValidity map(ch.naviqore.service.Validity validity) {
        return new ScheduleValidity(validity.getStartDate(), validity.getEndDate());
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
