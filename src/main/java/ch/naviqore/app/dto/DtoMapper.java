package ch.naviqore.app.dto;

import ch.naviqore.service.SearchType;
import ch.naviqore.service.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

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

    public static Connection map(ch.naviqore.service.Connection connection) {
        List<Leg> legs = connection.getLegs().stream().map(leg -> leg.accept(new LegVisitorImpl())).toList();
        return new Connection(legs);
    }

    public static EarliestArrival map(ch.naviqore.service.Stop stop, ch.naviqore.service.Connection connection) {
        return new EarliestArrival(map(stop), connection.getLegs().getLast().getArrivalTime(), map(connection));
    }

    private static class LegVisitorImpl implements LegVisitor<Leg> {
        @Override
        public Leg visit(PublicTransitLeg publicTransitLeg) {
            // TODO: Trip id in raptor has to be passed first.
            return new Leg(
                    // map(publicTransitLeg.getDeparture().getStop().getLocation()),
                    // map(publicTransitLeg.getArrival().getStop().getLocation()),
                    null, null,
                    // map(publicTransitLeg.getDeparture().getStop()),
                    // map(publicTransitLeg.getArrival().getStop()),
                    null, null, LegType.ROUTE,
                    // publicTransitLeg.getDeparture().getDepartureTime(),
                    // publicTransitLeg.getArrival().getArrivalTime(),
                    null, null,
                    // map(publicTransitLeg.getTrip())
                    null);
        }

        @Override
        public Leg visit(Transfer transfer) {
            return new Leg(transfer.getSourceStop().getLocation(), transfer.getTargetStop().getLocation(),
                    map(transfer.getSourceStop()), map(transfer.getTargetStop()), LegType.WALK,
                    transfer.getDepartureTime(), transfer.getArrivalTime(), null);
        }

        @Override
        public Leg visit(Walk walk) {
            return new Leg(walk.getSourceLocation(), walk.getTargetLocation(), null, null, LegType.WALK,
                    walk.getDepartureTime(), walk.getArrivalTime(), null);
        }
    }

}
