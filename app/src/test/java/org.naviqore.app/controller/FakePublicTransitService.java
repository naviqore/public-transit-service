package org.naviqore.app.controller;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.naviqore.service.*;
import org.naviqore.service.config.ConnectionQueryConfig;
import org.naviqore.service.exception.RouteNotFoundException;
import org.naviqore.service.exception.StopNotFoundException;
import org.naviqore.service.exception.TripNotActiveException;
import org.naviqore.service.exception.TripNotFoundException;
import org.naviqore.utils.spatial.GeoCoordinate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

@Setter
@NoArgsConstructor
class FakePublicTransitService implements PublicTransitService {

    public static final ZoneId ZONE_ID = ZoneId.of("Europe/Zurich");

    static final Models.Stop STOP_A = new Models.Stop("A", "Stop A", new GeoCoordinate(0, 0));
    static final Models.Stop STOP_B = new Models.Stop("B", "Stop B", new GeoCoordinate(1, 1));
    static final Models.Stop STOP_C = new Models.Stop("C", "Stop C", new GeoCoordinate(2, 2));
    static final Models.Stop STOP_D = new Models.Stop("D", "Stop D", new GeoCoordinate(3, 3));
    static final Models.Stop STOP_E = new Models.Stop("E", "Stop E", new GeoCoordinate(4, 4));
    static final Models.Stop STOP_F = new Models.Stop("F", "Stop F", new GeoCoordinate(5, 5));
    static final Models.Stop STOP_G = new Models.Stop("G", "Stop G", new GeoCoordinate(6, 6));
    static final Models.Stop STOP_H = new Models.Stop("H", "Stop H", new GeoCoordinate(7, 7));
    static final List<Models.Stop> STOPS = List.of(STOP_A, STOP_B, STOP_C, STOP_D, STOP_E, STOP_F, STOP_G, STOP_H);
    private static final RouteData ROUTE_1 = new RouteData(
            new Models.Route("1", "Route 1", "R1", TravelMode.BUS, "BUS", "Agency 1"),
            List.of(STOP_A, STOP_B, STOP_C, STOP_D, STOP_E, STOP_F, STOP_G));
    private static final RouteData ROUTE_2 = new RouteData(
            new Models.Route("2", "Route 2", "R2", TravelMode.BUS, "BUS", "Agency 2"),
            List.of(STOP_A, STOP_B, STOP_C, STOP_D));
    private static final RouteData ROUTE_3 = new RouteData(
            new Models.Route("3", "Route 3", "R3", TravelMode.BUS, "BUS", "Agency 3"),
            List.of(STOP_D, STOP_E, STOP_F, STOP_G, STOP_H));
    static final List<RouteData> ROUTES = List.of(ROUTE_1, ROUTE_2, ROUTE_3);

    private boolean supportsMaxWalkDuration = true;
    private boolean supportsMinTransferDuration = true;
    private boolean supportsMaxTransfers = true;
    private boolean supportsMaxTravelDuration = true;

    private boolean hasAccessibilityInformation = false;
    private boolean hasBikeInformation = false;
    private boolean hasTravelModeInformation = false;

    @Override
    public Validity getValidity() {
        return new Validity() {

            private static final int DELTA = 3;

            private final LocalDate now = LocalDate.now();

            @Override
            public LocalDate getStartDate() {
                return now.minusDays(DELTA);
            }

            @Override
            public LocalDate getEndDate() {
                return now.plusDays(DELTA);
            }

            @Override
            public boolean isWithin(LocalDate date) {
                return !date.isBefore(getStartDate()) && !date.isAfter(getEndDate());
            }
        };
    }

    @Override
    public RoutingFeatures getRoutingFeatures() {
        return new RoutingFeatures(supportsMaxTransfers, supportsMaxTravelDuration, supportsMaxWalkDuration,
                supportsMinTransferDuration, hasAccessibilityInformation, hasBikeInformation, hasTravelModeInformation);
    }

    @Override
    public boolean hasAccessibilityInformation() {
        return hasAccessibilityInformation;
    }

    @Override
    public boolean hasBikeInformation() {
        return hasBikeInformation;
    }

    @Override
    public boolean hasTravelModeInformation() {
        return hasTravelModeInformation;
    }

    @Override
    public List<Connection> getConnections(GeoCoordinate source, GeoCoordinate target, OffsetDateTime time,
                                           TimeType timeType, ConnectionQueryConfig config) {
        return List.of(ConnectionGenerators.getSimpleConnection(source, target, time, timeType));
    }

    @Override
    public List<Connection> getConnections(Stop source, Stop target, OffsetDateTime time, TimeType timeType,
                                           ConnectionQueryConfig config) {
        List<Connection> connections = new ArrayList<>();
        connections.add(ConnectionGenerators.getSimpleConnection(source, target, time, timeType));
        try {
            connections.add(ConnectionGenerators.getConnectionWithSameStopTransfer(source, target, time, timeType));
        } catch (IllegalArgumentException e) {
            // ignore
        }
        return connections;
    }

    @Override
    public List<Connection> getConnections(GeoCoordinate source, Stop target, OffsetDateTime time, TimeType timeType,
                                           ConnectionQueryConfig config) {
        return List.of(ConnectionGenerators.getSimpleConnection(source, target, time, timeType));
    }

    @Override
    public List<Connection> getConnections(Stop source, GeoCoordinate target, OffsetDateTime time, TimeType timeType,
                                           ConnectionQueryConfig config) {
        return List.of(ConnectionGenerators.getSimpleConnection(source, target, time, timeType));
    }

    @Override
    public Map<Stop, Connection> getIsolines(GeoCoordinate source, OffsetDateTime time, TimeType timeType,
                                             ConnectionQueryConfig config) {
        Map<Stop, Connection> connections = new HashMap<>();
        for (Stop stop : STOPS) {
            try {
                if (timeType == TimeType.DEPARTURE) {
                    connections.put(stop, ConnectionGenerators.getSimpleConnection(source, stop, time, timeType));
                } else {
                    connections.put(stop, ConnectionGenerators.getSimpleConnection(stop, source, time, timeType));
                }
            } catch (IllegalArgumentException e) {
                // ignore
            }
        }
        return connections;
    }

    @Override
    public Map<Stop, Connection> getIsolines(Stop source, OffsetDateTime time, TimeType timeType,
                                             ConnectionQueryConfig config) {
        Map<Stop, Connection> connections = new HashMap<>();
        for (Stop stop : STOPS) {
            if (source == stop) {
                continue;
            }
            try {
                if (timeType == TimeType.DEPARTURE) {
                    connections.put(stop, ConnectionGenerators.getSimpleConnection(source, stop, time, timeType));
                } else {
                    connections.put(stop, ConnectionGenerators.getSimpleConnection(stop, source, time, timeType));
                }
            } catch (IllegalArgumentException e) {
                // ignore
            }
        }
        return connections;
    }

    @Override
    public List<Stop> getStops(String like, SearchType searchType, StopSortStrategy stopSortStrategy) {
        return STOPS.stream().map(x -> (Stop) x).toList();
    }

    @Override
    public Optional<Stop> getNearestStop(GeoCoordinate location) {
        return Optional.of(STOP_A);
    }

    @Override
    public List<Stop> getNearestStops(GeoCoordinate location, int radius) {
        if (radius > 100) {
            return List.of(STOP_A, STOP_B, STOP_C);
        } else {
            return List.of();
        }
    }

    @Override
    public List<StopTime> getStopTimes(Stop stop, OffsetDateTime from, OffsetDateTime to, TimeType timeType,
                                       StopScope stopScope) {
        return List.of();
    }

    @Override
    public Stop getStopById(String stopId) throws StopNotFoundException {
        return STOPS.stream()
                .filter(stop -> stop.getId().equals(stopId))
                .findFirst()
                .orElseThrow(() -> new StopNotFoundException(stopId));
    }

    @Override
    public Trip getTripById(String tripId, LocalDate date) throws TripNotFoundException, TripNotActiveException {
        if (tripId.equals("not_existing_trip")) {
            throw new TripNotFoundException(tripId);
        } else if (date.isEqual(LocalDate.of(2021, 1, 1))) {
            throw new TripNotActiveException(tripId, date);
        } else {
            PublicTransitLeg leg = ConnectionGenerators.getPublicTransitLeg(ROUTE_1, STOP_A, STOP_G,
                    date.atTime(8, 0).atZone(ZONE_ID).toOffsetDateTime(), TimeType.DEPARTURE);
            return leg.getTrip();
        }
    }

    @Override
    public Route getRouteById(String routeId) throws RouteNotFoundException {
        return ROUTES.stream()
                .filter(routeData -> routeData.route.getId().equals(routeId))
                .map(routeData -> routeData.route)
                .findFirst()
                .orElseThrow(() -> new RouteNotFoundException(routeId));
    }

    private record RouteData(Models.Route route, List<Models.Stop> stops) {
    }

    private static class ConnectionGenerators {
        private static final int SECONDS_BETWEEN_STOPS = 300;
        private static final int DISTANCE_BETWEEN_STOPS = 100;

        static Models.Connection getSimpleConnection(Stop startStop, Stop endStop, OffsetDateTime date,
                                                     TimeType timeType) {
            if (startStop == endStop) {
                throw new IllegalArgumentException("Start and end stop must be different.");
            } else if (endStop == STOP_H) {
                // downcast start stop to DummyServiceModels.Stop
                return getConnectionWithFinalWalkTransfer((Models.Stop) startStop, (Models.Stop) endStop, date,
                        timeType);
            }
            Models.PublicTransitLeg leg = getPublicTransitLeg(ROUTE_1, startStop, endStop, date, timeType);
            return new Models.Connection(List.of(leg));
        }

        static Models.Connection getConnectionWithSameStopTransfer(Stop startStop, Stop endStop, OffsetDateTime date,
                                                                   TimeType timeType) {
            if (startStop == endStop) {
                throw new IllegalArgumentException("Start and end stop must be different.");
            } else if (startStop == STOP_D || endStop == STOP_D) {
                throw new IllegalArgumentException("Stop D cannot be used for same stop transfer.");
            } else if (!ROUTE_3.stops().contains((Models.Stop) endStop)) {
                throw new IllegalArgumentException("End stop must be part of Route 3.");
            } else if (!ROUTE_2.stops().contains((Models.Stop) startStop)) {
                throw new IllegalArgumentException("Start stop must be part of Route 2.");
            }
            Models.PublicTransitLeg firstLeg;
            Models.PublicTransitLeg secondLeg;
            if (timeType == TimeType.DEPARTURE) {
                firstLeg = getPublicTransitLeg(ROUTE_2, startStop, STOP_D, date, timeType);
                OffsetDateTime departureSecondLeg = firstLeg.getArrival().getArrivalTime().plusMinutes(5);
                secondLeg = getPublicTransitLeg(ROUTE_3, STOP_D, endStop, departureSecondLeg, timeType);
            } else {
                secondLeg = getPublicTransitLeg(ROUTE_3, STOP_D, endStop, date, timeType);
                OffsetDateTime departureFirstLeg = secondLeg.getDeparture().getDepartureTime().minusMinutes(5);
                firstLeg = getPublicTransitLeg(ROUTE_2, startStop, STOP_D, departureFirstLeg, timeType);
            }

            return new Models.Connection(List.of(firstLeg, secondLeg));
        }

        static Models.Connection getConnectionWithFinalWalkTransfer(Models.Stop startStop, Models.Stop endStop,
                                                                    OffsetDateTime date, TimeType timeType) {
            if (startStop == endStop) {
                throw new IllegalArgumentException("Start and end stop must be different.");
            }
            int endStopIndex = STOPS.indexOf(endStop);
            if (endStopIndex == -1) {
                throw new IllegalArgumentException("End stop not found in stops.");
            }
            int startStopIndex = STOPS.indexOf(startStop);
            if (startStopIndex == -1) {
                throw new IllegalArgumentException("Start stop not found in stops.");
            }
            if (endStopIndex < startStopIndex + 2) {
                throw new IllegalArgumentException("End stop must be at least two stops after start stop.");
            }
            Models.Stop routeEndStop = STOPS.get(endStopIndex - 1);
            Models.PublicTransitLeg leg;
            Models.Transfer transfer;
            if (timeType == TimeType.DEPARTURE) {
                leg = getPublicTransitLeg(ROUTE_1, startStop, routeEndStop, date, timeType);
                OffsetDateTime departureWalk = leg.getArrival().getArrivalTime();
                int duration = 2 * SECONDS_BETWEEN_STOPS;
                transfer = new Models.Transfer(DISTANCE_BETWEEN_STOPS, duration, departureWalk,
                        departureWalk.plusSeconds(duration), routeEndStop, endStop);
            } else {
                int duration = 2 * SECONDS_BETWEEN_STOPS;
                transfer = new Models.Transfer(DISTANCE_BETWEEN_STOPS, duration, date.minusSeconds(duration), date,
                        startStop, routeEndStop);
                leg = getPublicTransitLeg(ROUTE_1, routeEndStop, endStop, date.minusSeconds(duration), timeType);

            }
            return new Models.Connection(List.of(leg, transfer));
        }

        static Models.Connection getSimpleConnection(GeoCoordinate startCoordinate, Stop endStop, OffsetDateTime date,
                                                     TimeType timeType) {
            if (!ROUTE_1.stops().contains((Models.Stop) endStop)) {
                throw new IllegalArgumentException("End stop must be part of Route 1.");
            } else if (endStop == STOP_A) {
                throw new IllegalArgumentException("Stop A cannot be used as end stop.");
            }
            Models.Stop routeStartStop = STOP_A;
            Models.Walk walk;
            Models.PublicTransitLeg leg;
            int walkDuration = 2 * SECONDS_BETWEEN_STOPS;
            if (timeType == TimeType.DEPARTURE) {
                walk = new Models.Walk(DISTANCE_BETWEEN_STOPS, walkDuration, WalkType.FIRST_MILE, date,
                        date.plusSeconds(walkDuration), startCoordinate, routeStartStop.getCoordinate(),
                        routeStartStop);
                leg = getPublicTransitLeg(ROUTE_1, routeStartStop, endStop, date.plusSeconds(walkDuration), timeType);
            } else {
                leg = getPublicTransitLeg(ROUTE_1, routeStartStop, endStop, date, timeType);
                OffsetDateTime legArrival = leg.getArrival().getArrivalTime();
                walk = new Models.Walk(DISTANCE_BETWEEN_STOPS, walkDuration, WalkType.FIRST_MILE,
                        legArrival.minusSeconds(walkDuration), legArrival, routeStartStop.getCoordinate(),
                        endStop.getCoordinate(), routeStartStop);
            }
            return new Models.Connection(List.of(walk, leg));

        }

        static Models.Connection getSimpleConnection(Stop startStop, GeoCoordinate endCoordinate, OffsetDateTime date,
                                                     TimeType timeType) {
            if (!ROUTE_1.stops().contains((Models.Stop) startStop)) {
                throw new IllegalArgumentException("End stop must be part of Route 1.");
            } else if (startStop == STOP_G) {
                throw new IllegalArgumentException("Stop G cannot be used as start stop.");
            }
            Models.Stop routeEndStop = STOP_G;
            Models.Walk walk;
            Models.PublicTransitLeg leg;
            int walkDuration = 2 * SECONDS_BETWEEN_STOPS;
            if (timeType == TimeType.DEPARTURE) {
                leg = getPublicTransitLeg(ROUTE_1, startStop, routeEndStop, date, timeType);
                OffsetDateTime legArrival = leg.getArrival().getArrivalTime();
                walk = new Models.Walk(DISTANCE_BETWEEN_STOPS, walkDuration, WalkType.LAST_MILE, legArrival,
                        legArrival.plusSeconds(walkDuration), routeEndStop.getCoordinate(), endCoordinate,
                        routeEndStop);
            } else {
                OffsetDateTime walkDeparture = date.minusSeconds(walkDuration);
                walk = new Models.Walk(DISTANCE_BETWEEN_STOPS, walkDuration, WalkType.LAST_MILE, walkDeparture, date,
                        routeEndStop.getCoordinate(), endCoordinate, routeEndStop);
                leg = getPublicTransitLeg(ROUTE_1, startStop, routeEndStop, walkDeparture, timeType);
            }
            return new Models.Connection(List.of(leg, walk));
        }

        static Models.Connection getSimpleConnection(GeoCoordinate startCoordinate, GeoCoordinate endCoordinate,
                                                     OffsetDateTime date, TimeType timeType) {
            Models.Stop routeStartStop = STOP_A;
            Models.Stop routeEndStop = STOP_G;
            Models.Walk firstWalk;
            Models.Walk lastWalk;
            Models.PublicTransitLeg leg;
            int walkDuration = 2 * SECONDS_BETWEEN_STOPS;
            if (timeType == TimeType.DEPARTURE) {
                firstWalk = new Models.Walk(DISTANCE_BETWEEN_STOPS, walkDuration, WalkType.FIRST_MILE, date,
                        date.plusSeconds(walkDuration), startCoordinate, routeStartStop.getCoordinate(),
                        routeStartStop);
                leg = getPublicTransitLeg(ROUTE_1, routeStartStop, routeEndStop, date.plusSeconds(walkDuration),
                        timeType);
                OffsetDateTime legArrival = leg.getArrival().getArrivalTime();
                lastWalk = new Models.Walk(DISTANCE_BETWEEN_STOPS, walkDuration, WalkType.LAST_MILE, legArrival,
                        legArrival.plusSeconds(walkDuration), routeEndStop.getCoordinate(), endCoordinate,
                        routeEndStop);
            } else {
                OffsetDateTime walkDeparture = date.minusSeconds(walkDuration);
                lastWalk = new Models.Walk(DISTANCE_BETWEEN_STOPS, walkDuration, WalkType.LAST_MILE, walkDeparture,
                        date, routeEndStop.getCoordinate(), endCoordinate, routeEndStop);
                leg = getPublicTransitLeg(ROUTE_1, routeStartStop, routeEndStop, walkDeparture, timeType);
                OffsetDateTime legDeparture = leg.getDeparture().getDepartureTime();
                firstWalk = new Models.Walk(DISTANCE_BETWEEN_STOPS, walkDuration, WalkType.FIRST_MILE,
                        legDeparture.minusSeconds(walkDuration), legDeparture, startCoordinate,
                        routeStartStop.getCoordinate(), routeStartStop);
            }
            return new Models.Connection(List.of(firstWalk, leg, lastWalk));
        }

        private static Models.PublicTransitLeg getPublicTransitLeg(RouteData route, Stop startStop, Stop endStop,
                                                                   OffsetDateTime startTime, TimeType timeType) {
            // get index of reference stop in route.stops
            int startStopIndex = route.stops().indexOf((Models.Stop) startStop);
            if (startStopIndex == -1) {
                throw new IllegalArgumentException("Start stop not found in route.");
            }
            int endStopIndex = route.stops().indexOf((Models.Stop) endStop);
            if (endStopIndex == -1) {
                throw new IllegalArgumentException("End stop not found in route.");
            } else if (endStopIndex < startStopIndex) {
                throw new IllegalArgumentException("End stop must be after start stop.");
            }

            int refIndex = timeType == TimeType.DEPARTURE ? startStopIndex : endStopIndex;

            Models.Trip trip = new Models.Trip(route.route().getId() + "_" + startStop.getId(), "Head Sign",
                    route.route(), false, false);
            List<org.naviqore.service.StopTime> stopTimes = new ArrayList<>();
            for (int i = 0; i < route.stops().size(); i++) {
                Models.Stop stop = route.stops().get(i);
                OffsetDateTime arrivalTime = startTime.plusSeconds((long) SECONDS_BETWEEN_STOPS * (i - refIndex));
                stopTimes.add(new Models.StopTime(stop, arrivalTime, arrivalTime, trip));
            }
            trip.setStopTimes(stopTimes);

            Models.StopTime departure = (Models.StopTime) stopTimes.get(startStopIndex);
            Models.StopTime arrival = (Models.StopTime) stopTimes.get(endStopIndex);

            int duration = SECONDS_BETWEEN_STOPS * (endStopIndex - startStopIndex);
            int distance = DISTANCE_BETWEEN_STOPS * (endStopIndex - startStopIndex);

            return new Models.PublicTransitLeg(distance, duration, trip, departure, arrival);
        }

    }

    static class Models {

        @RequiredArgsConstructor
        @Getter
        static abstract class Leg implements org.naviqore.service.Leg {
            private final LegType legType;
            private final int distance;
            private final int duration;

            @Override
            public abstract <T> T accept(LegVisitor<T> visitor);
        }

        @Getter
        static class PublicTransitLeg extends Leg implements org.naviqore.service.PublicTransitLeg {

            private final Trip trip;
            private final StopTime departure;
            private final StopTime arrival;

            PublicTransitLeg(int distance, int duration, Trip trip, StopTime departure, StopTime arrival) {
                super(LegType.PUBLIC_TRANSIT, distance, duration);
                this.trip = trip;
                this.departure = departure;
                this.arrival = arrival;
            }

            @Override
            public <T> T accept(LegVisitor<T> visitor) {
                return visitor.visit(this);
            }
        }

        @Getter
        static class Transfer extends Leg implements org.naviqore.service.Transfer {
            private final OffsetDateTime departureTime;
            private final OffsetDateTime arrivalTime;
            private final Stop sourceStop;
            private final Stop targetStop;

            Transfer(int distance, int duration, OffsetDateTime departureTime, OffsetDateTime arrivalTime,
                     Stop sourceStop, Stop targetStop) {
                super(LegType.WALK, distance, duration);
                this.departureTime = departureTime;
                this.arrivalTime = arrivalTime;
                this.sourceStop = sourceStop;
                this.targetStop = targetStop;
            }

            @Override
            public <T> T accept(LegVisitor<T> visitor) {
                return visitor.visit(this);
            }
        }

        @Getter
        static class Walk extends Leg implements org.naviqore.service.Walk {
            private final WalkType walkType;
            private final OffsetDateTime departureTime;
            private final OffsetDateTime arrivalTime;
            private final GeoCoordinate sourceLocation;
            private final GeoCoordinate targetLocation;
            private final Stop stop;

            Walk(int distance, int duration, WalkType walkType, OffsetDateTime departureTime,
                 OffsetDateTime arrivalTime, GeoCoordinate sourceLocation, GeoCoordinate targetLocation,
                 Models.@Nullable Stop stop) {
                super(LegType.WALK, distance, duration);
                this.walkType = walkType;
                this.departureTime = departureTime;
                this.arrivalTime = arrivalTime;
                this.sourceLocation = sourceLocation;
                this.targetLocation = targetLocation;
                this.stop = stop;
            }

            @Override
            public <T> T accept(LegVisitor<T> visitor) {
                return visitor.visit(this);
            }

            @Override
            public Optional<org.naviqore.service.Stop> getStop() {
                return Optional.ofNullable(stop);
            }
        }

        @RequiredArgsConstructor
        @Getter
        static class Route implements org.naviqore.service.Route {
            private final String id;
            private final String name;
            private final String shortName;
            private final TravelMode routeType;
            private final String routeTypeDescription;
            private final String Agency;

        }

        @RequiredArgsConstructor
        @Getter
        static class Trip implements org.naviqore.service.Trip {
            private final String id;
            private final String headSign;
            private final Route route;
            private final boolean bikesAllowed;
            private final boolean wheelchairAccessible;
            @Setter
            private List<org.naviqore.service.StopTime> stopTimes;

        }

        @RequiredArgsConstructor
        @Getter
        static class Stop implements org.naviqore.service.Stop {
            private final String id;
            private final String name;
            private final GeoCoordinate coordinate;
        }

        @RequiredArgsConstructor
        @Getter
        static class StopTime implements org.naviqore.service.StopTime {
            private final Stop stop;
            private final OffsetDateTime arrivalTime;
            private final OffsetDateTime departureTime;
            private final transient Trip trip;
        }

        @RequiredArgsConstructor
        static class Connection implements org.naviqore.service.Connection {

            private final List<Leg> legs;

            @Override
            public List<org.naviqore.service.Leg> getLegs() {
                return legs.stream().map(leg -> (org.naviqore.service.Leg) leg).toList();
            }

            @Override
            public OffsetDateTime getDepartureTime() {
                return legs.getFirst().accept(new LegVisitor<>() {
                    @Override
                    public OffsetDateTime visit(org.naviqore.service.PublicTransitLeg publicTransitLeg) {
                        return publicTransitLeg.getDeparture().getDepartureTime();
                    }

                    @Override
                    public OffsetDateTime visit(org.naviqore.service.Transfer transfer) {
                        return transfer.getDepartureTime();
                    }

                    @Override
                    public OffsetDateTime visit(org.naviqore.service.Walk walk) {
                        return walk.getDepartureTime();
                    }
                });
            }

            @Override
            public OffsetDateTime getArrivalTime() {
                return legs.getLast().accept(new LegVisitor<>() {
                    @Override
                    public OffsetDateTime visit(org.naviqore.service.PublicTransitLeg publicTransitLeg) {
                        return publicTransitLeg.getArrival().getArrivalTime();
                    }

                    @Override
                    public OffsetDateTime visit(org.naviqore.service.Transfer transfer) {
                        return transfer.getArrivalTime();
                    }

                    @Override
                    public OffsetDateTime visit(org.naviqore.service.Walk walk) {
                        return walk.getArrivalTime();
                    }
                });
            }
        }
    }
}
