package ch.naviqore.app.controller;

import ch.naviqore.service.*;
import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.service.exception.RouteNotFoundException;
import ch.naviqore.service.exception.StopNotFoundException;
import ch.naviqore.service.exception.TripNotActiveException;
import ch.naviqore.service.exception.TripNotFoundException;
import ch.naviqore.utils.spatial.GeoCoordinate;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Setter
@NoArgsConstructor
class DummyService implements PublicTransitService {

    static final DummyServiceModels.Stop STOP_A = new DummyServiceModels.Stop("A", "Stop A", new GeoCoordinate(0, 0));
    static final DummyServiceModels.Stop STOP_B = new DummyServiceModels.Stop("B", "Stop B", new GeoCoordinate(1, 1));
    static final DummyServiceModels.Stop STOP_C = new DummyServiceModels.Stop("C", "Stop C", new GeoCoordinate(2, 2));
    static final DummyServiceModels.Stop STOP_D = new DummyServiceModels.Stop("D", "Stop D", new GeoCoordinate(3, 3));
    static final DummyServiceModels.Stop STOP_E = new DummyServiceModels.Stop("E", "Stop E", new GeoCoordinate(4, 4));
    static final DummyServiceModels.Stop STOP_F = new DummyServiceModels.Stop("F", "Stop F", new GeoCoordinate(5, 5));
    static final DummyServiceModels.Stop STOP_G = new DummyServiceModels.Stop("G", "Stop G", new GeoCoordinate(6, 6));
    static final DummyServiceModels.Stop STOP_H = new DummyServiceModels.Stop("H", "Stop H", new GeoCoordinate(7, 7));
    static final List<DummyServiceModels.Stop> STOPS = List.of(STOP_A, STOP_B, STOP_C, STOP_D, STOP_E, STOP_F, STOP_G,
            STOP_H);
    private static final RouteData ROUTE_1 = new RouteData(
            new DummyServiceModels.Route("1", "Route 1", "R1", TravelMode.BUS, "BUS", "Agency 1"),
            List.of(STOP_A, STOP_B, STOP_C, STOP_D, STOP_E, STOP_F, STOP_G));
    private static final RouteData ROUTE_2 = new RouteData(
            new DummyServiceModels.Route("2", "Route 2", "R2", TravelMode.BUS, "BUS", "Agency 2"),
            List.of(STOP_A, STOP_B, STOP_C, STOP_D));
    private static final RouteData ROUTE_3 = new RouteData(
            new DummyServiceModels.Route("3", "Route 3", "R3", TravelMode.BUS, "BUS", "Agency 3"),
            List.of(STOP_D, STOP_E, STOP_F, STOP_G, STOP_H));
    static final List<RouteData> ROUTES = List.of(ROUTE_1, ROUTE_2, ROUTE_3);

    private boolean supportsMaxWalkingDuration = true;
    private boolean supportsMinTransferDuration = true;
    private boolean supportsMaxTransferNumber = true;
    private boolean supportsMaxTravelTime = true;

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
        return new RoutingFeatures(supportsMaxTransferNumber, supportsMaxTravelTime, supportsMaxWalkingDuration,
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
    public List<Connection> getConnections(GeoCoordinate source, GeoCoordinate target, LocalDateTime time,
                                           TimeType timeType, ConnectionQueryConfig config) {
        return List.of(DummyConnectionGenerators.getSimpleConnection(source, target, time, timeType));
    }

    @Override
    public List<Connection> getConnections(Stop source, Stop target, LocalDateTime time, TimeType timeType,
                                           ConnectionQueryConfig config) {
        List<Connection> connections = new ArrayList<>();
        connections.add(DummyConnectionGenerators.getSimpleConnection(source, target, time, timeType));
        try {
            connections.add(
                    DummyConnectionGenerators.getConnectionWithSameStopTransfer(source, target, time, timeType));
        } catch (IllegalArgumentException e) {
            // ignore
        }
        return connections;
    }

    @Override
    public List<Connection> getConnections(GeoCoordinate source, Stop target, LocalDateTime time, TimeType timeType,
                                           ConnectionQueryConfig config) {
        return List.of(DummyConnectionGenerators.getSimpleConnection(source, target, time, timeType));
    }

    @Override
    public List<Connection> getConnections(Stop source, GeoCoordinate target, LocalDateTime time, TimeType timeType,
                                           ConnectionQueryConfig config) {
        return List.of(DummyConnectionGenerators.getSimpleConnection(source, target, time, timeType));
    }

    @Override
    public Map<Stop, Connection> getIsolines(GeoCoordinate source, LocalDateTime time, TimeType timeType,
                                             ConnectionQueryConfig config) {
        Map<Stop, Connection> connections = new HashMap<>();
        for (Stop stop : STOPS) {
            try {
                if (timeType == TimeType.DEPARTURE) {
                    connections.put(stop, DummyConnectionGenerators.getSimpleConnection(source, stop, time, timeType));
                } else {
                    connections.put(stop, DummyConnectionGenerators.getSimpleConnection(stop, source, time, timeType));
                }
            } catch (IllegalArgumentException e) {
                // ignore
            }
        }
        return connections;
    }

    @Override
    public Map<Stop, Connection> getIsolines(Stop source, LocalDateTime time, TimeType timeType,
                                             ConnectionQueryConfig config) {
        Map<Stop, Connection> connections = new HashMap<>();
        for (Stop stop : STOPS) {
            if (source == stop) {
                continue;
            }
            try {
                if (timeType == TimeType.DEPARTURE) {
                    connections.put(stop, DummyConnectionGenerators.getSimpleConnection(source, stop, time, timeType));
                } else {
                    connections.put(stop, DummyConnectionGenerators.getSimpleConnection(stop, source, time, timeType));
                }
            } catch (IllegalArgumentException e) {
                // ignore
            }
        }
        return connections;
    }

    @Override
    public List<Stop> getStops(String like, SearchType searchType) {
        return STOPS.stream().map(x -> (Stop) x).toList();
    }

    @Override
    public Optional<Stop> getNearestStop(GeoCoordinate location) {
        return Optional.of(STOP_A);
    }

    @Override
    public List<Stop> getNearestStops(GeoCoordinate location, int radius, int limit) {
        if (radius > 100) {
            return List.of(STOP_A, STOP_B, STOP_C);
        } else {
            return List.of();
        }
    }

    @Override
    public List<StopTime> getNextDepartures(Stop stop, LocalDateTime from, @Nullable LocalDateTime until, int limit) {
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
            PublicTransitLeg leg = DummyConnectionGenerators.getPublicTransitLeg(ROUTE_1, STOP_A, STOP_G,
                    date.atTime(8, 0), TimeType.DEPARTURE);
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

    private record RouteData(DummyServiceModels.Route route, List<DummyServiceModels.Stop> stops) {
    }

    static class DummyConnectionGenerators {
        private static final int SECONDS_BETWEEN_STOPS = 300;
        private static final int DISTANCE_BETWEEN_STOPS = 100;

        static DummyServiceModels.Connection getSimpleConnection(Stop startStop, Stop endStop, LocalDateTime date,
                                                                 TimeType timeType) {
            if (startStop == endStop) {
                throw new IllegalArgumentException("Start and end stop must be different.");
            } else if (endStop == STOP_H) {
                // downcast start stop to DummyServiceModels.Stop
                return getConnectionWithFinalWalkTransfer((DummyServiceModels.Stop) startStop,
                        (DummyServiceModels.Stop) endStop, date, timeType);
            }
            DummyServiceModels.PublicTransitLeg leg = getPublicTransitLeg(ROUTE_1, startStop, endStop, date, timeType);
            return new DummyServiceModels.Connection(List.of(leg));
        }

        static DummyServiceModels.Connection getConnectionWithSameStopTransfer(Stop startStop, Stop endStop,
                                                                               LocalDateTime date, TimeType timeType) {
            if (startStop == endStop) {
                throw new IllegalArgumentException("Start and end stop must be different.");
            } else if (startStop == STOP_D || endStop == STOP_D) {
                throw new IllegalArgumentException("Stop D cannot be used for same stop transfer.");
            } else if (!ROUTE_3.stops().contains((DummyServiceModels.Stop) endStop)) {
                throw new IllegalArgumentException("End stop must be part of Route 3.");
            } else if (!ROUTE_2.stops().contains((DummyServiceModels.Stop) startStop)) {
                throw new IllegalArgumentException("Start stop must be part of Route 2.");
            }
            DummyServiceModels.PublicTransitLeg firstLeg;
            DummyServiceModels.PublicTransitLeg secondLeg;
            if (timeType == TimeType.DEPARTURE) {
                firstLeg = getPublicTransitLeg(ROUTE_2, startStop, STOP_D, date, timeType);
                LocalDateTime departureSecondLeg = firstLeg.getArrival().getArrivalTime().plusMinutes(5);
                secondLeg = getPublicTransitLeg(ROUTE_3, STOP_D, endStop, departureSecondLeg, timeType);
            } else {
                secondLeg = getPublicTransitLeg(ROUTE_3, STOP_D, endStop, date, timeType);
                LocalDateTime departureFirstLeg = secondLeg.getDeparture().getDepartureTime().minusMinutes(5);
                firstLeg = getPublicTransitLeg(ROUTE_2, startStop, STOP_D, departureFirstLeg, timeType);
            }

            return new DummyServiceModels.Connection(List.of(firstLeg, secondLeg));
        }

        static DummyServiceModels.Connection getConnectionWithFinalWalkTransfer(DummyServiceModels.Stop startStop,
                                                                                DummyServiceModels.Stop endStop,
                                                                                LocalDateTime date, TimeType timeType) {
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
            DummyServiceModels.Stop routeEndStop = STOPS.get(endStopIndex - 1);
            DummyServiceModels.PublicTransitLeg leg;
            DummyServiceModels.Transfer transfer;
            if (timeType == TimeType.DEPARTURE) {
                leg = getPublicTransitLeg(ROUTE_1, startStop, routeEndStop, date, timeType);
                LocalDateTime departureWalk = leg.getArrival().getArrivalTime();
                int duration = 2 * SECONDS_BETWEEN_STOPS;
                transfer = new DummyServiceModels.Transfer(DISTANCE_BETWEEN_STOPS, duration, departureWalk,
                        departureWalk.plusSeconds(duration), routeEndStop, endStop);
            } else {
                int duration = 2 * SECONDS_BETWEEN_STOPS;
                transfer = new DummyServiceModels.Transfer(DISTANCE_BETWEEN_STOPS, duration,
                        date.minusSeconds(duration), date, startStop, routeEndStop);
                leg = getPublicTransitLeg(ROUTE_1, routeEndStop, endStop, date.minusSeconds(duration), timeType);

            }
            return new DummyServiceModels.Connection(List.of(leg, transfer));
        }

        static DummyServiceModels.Connection getSimpleConnection(GeoCoordinate startCoordinate, Stop endStop,
                                                                 LocalDateTime date, TimeType timeType) {
            if (!ROUTE_1.stops().contains((DummyServiceModels.Stop) endStop)) {
                throw new IllegalArgumentException("End stop must be part of Route 1.");
            } else if (endStop == STOP_A) {
                throw new IllegalArgumentException("Stop A cannot be used as end stop.");
            }
            DummyServiceModels.Stop routeStartStop = STOP_A;
            DummyServiceModels.Walk walk;
            DummyServiceModels.PublicTransitLeg leg;
            int walkDuration = 2 * SECONDS_BETWEEN_STOPS;
            if (timeType == TimeType.DEPARTURE) {
                walk = new DummyServiceModels.Walk(DISTANCE_BETWEEN_STOPS, walkDuration, WalkType.FIRST_MILE, date,
                        date.plusSeconds(walkDuration), startCoordinate, routeStartStop.getLocation(), routeStartStop);
                leg = getPublicTransitLeg(ROUTE_1, routeStartStop, endStop, date.plusSeconds(walkDuration), timeType);
            } else {
                leg = getPublicTransitLeg(ROUTE_1, routeStartStop, endStop, date, timeType);
                LocalDateTime legArrival = leg.getArrival().getArrivalTime();
                walk = new DummyServiceModels.Walk(DISTANCE_BETWEEN_STOPS, walkDuration, WalkType.FIRST_MILE,
                        legArrival.minusSeconds(walkDuration), legArrival, routeStartStop.getLocation(),
                        endStop.getLocation(), routeStartStop);
            }
            return new DummyServiceModels.Connection(List.of(walk, leg));

        }

        static DummyServiceModels.Connection getSimpleConnection(Stop startStop, GeoCoordinate endCoordinate,
                                                                 LocalDateTime date, TimeType timeType) {
            if (!ROUTE_1.stops().contains((DummyServiceModels.Stop) startStop)) {
                throw new IllegalArgumentException("End stop must be part of Route 1.");
            } else if (startStop == STOP_G) {
                throw new IllegalArgumentException("Stop G cannot be used as start stop.");
            }
            DummyServiceModels.Stop routeEndStop = STOP_G;
            DummyServiceModels.Walk walk;
            DummyServiceModels.PublicTransitLeg leg;
            int walkDuration = 2 * SECONDS_BETWEEN_STOPS;
            if (timeType == TimeType.DEPARTURE) {
                leg = getPublicTransitLeg(ROUTE_1, startStop, routeEndStop, date, timeType);
                LocalDateTime legArrival = leg.getArrival().getArrivalTime();
                walk = new DummyServiceModels.Walk(DISTANCE_BETWEEN_STOPS, walkDuration, WalkType.LAST_MILE, legArrival,
                        legArrival.plusSeconds(walkDuration), routeEndStop.getLocation(), endCoordinate, routeEndStop);
            } else {
                LocalDateTime walkDeparture = date.minusSeconds(walkDuration);
                walk = new DummyServiceModels.Walk(DISTANCE_BETWEEN_STOPS, walkDuration, WalkType.LAST_MILE,
                        walkDeparture, date, routeEndStop.getLocation(), endCoordinate, routeEndStop);
                leg = getPublicTransitLeg(ROUTE_1, startStop, routeEndStop, walkDeparture, timeType);
            }
            return new DummyServiceModels.Connection(List.of(leg, walk));
        }

        static DummyServiceModels.Connection getSimpleConnection(GeoCoordinate startCoordinate,
                                                                 GeoCoordinate endCoordinate, LocalDateTime date,
                                                                 TimeType timeType) {
            DummyServiceModels.Stop routeStartStop = STOP_A;
            DummyServiceModels.Stop routeEndStop = STOP_G;
            DummyServiceModels.Walk firstWalk;
            DummyServiceModels.Walk lastWalk;
            DummyServiceModels.PublicTransitLeg leg;
            int walkDuration = 2 * SECONDS_BETWEEN_STOPS;
            if (timeType == TimeType.DEPARTURE) {
                firstWalk = new DummyServiceModels.Walk(DISTANCE_BETWEEN_STOPS, walkDuration, WalkType.FIRST_MILE, date,
                        date.plusSeconds(walkDuration), startCoordinate, routeStartStop.getLocation(), routeStartStop);
                leg = getPublicTransitLeg(ROUTE_1, routeStartStop, routeEndStop, date.plusSeconds(walkDuration),
                        timeType);
                LocalDateTime legArrival = leg.getArrival().getArrivalTime();
                lastWalk = new DummyServiceModels.Walk(DISTANCE_BETWEEN_STOPS, walkDuration, WalkType.LAST_MILE,
                        legArrival, legArrival.plusSeconds(walkDuration), routeEndStop.getLocation(), endCoordinate,
                        routeEndStop);
            } else {
                LocalDateTime walkDeparture = date.minusSeconds(walkDuration);
                lastWalk = new DummyServiceModels.Walk(DISTANCE_BETWEEN_STOPS, walkDuration, WalkType.LAST_MILE,
                        walkDeparture, date, routeEndStop.getLocation(), endCoordinate, routeEndStop);
                leg = getPublicTransitLeg(ROUTE_1, routeStartStop, routeEndStop, walkDeparture, timeType);
                LocalDateTime legDeparture = leg.getDeparture().getDepartureTime();
                firstWalk = new DummyServiceModels.Walk(DISTANCE_BETWEEN_STOPS, walkDuration, WalkType.FIRST_MILE,
                        legDeparture.minusSeconds(walkDuration), legDeparture, startCoordinate,
                        routeStartStop.getLocation(), routeStartStop);
            }
            return new DummyServiceModels.Connection(List.of(firstWalk, leg, lastWalk));
        }

        private static DummyServiceModels.PublicTransitLeg getPublicTransitLeg(RouteData route, Stop startStop,
                                                                               Stop endStop, LocalDateTime startTime,
                                                                               TimeType timeType) {
            // get index of reference stop in route.stops
            int startStopIndex = route.stops().indexOf((DummyServiceModels.Stop) startStop);
            if (startStopIndex == -1) {
                throw new IllegalArgumentException("Start stop not found in route.");
            }
            int endStopIndex = route.stops().indexOf((DummyServiceModels.Stop) endStop);
            if (endStopIndex == -1) {
                throw new IllegalArgumentException("End stop not found in route.");
            } else if (endStopIndex < startStopIndex) {
                throw new IllegalArgumentException("End stop must be after start stop.");
            }

            int refIndex = timeType == TimeType.DEPARTURE ? startStopIndex : endStopIndex;

            DummyServiceModels.Trip trip = new DummyServiceModels.Trip(route.route().getId() + "_" + startStop.getId(),
                    "Head Sign", route.route(), false, false);
            List<ch.naviqore.service.StopTime> stopTimes = new ArrayList<>();
            for (int i = 0; i < route.stops().size(); i++) {
                DummyServiceModels.Stop stop = route.stops().get(i);
                LocalDateTime arrivalTime = startTime.plusSeconds((long) SECONDS_BETWEEN_STOPS * (i - refIndex));
                stopTimes.add(new DummyServiceModels.StopTime(stop, arrivalTime, arrivalTime, trip));
            }
            trip.setStopTimes(stopTimes);

            DummyServiceModels.StopTime departure = (DummyServiceModels.StopTime) stopTimes.get(startStopIndex);
            DummyServiceModels.StopTime arrival = (DummyServiceModels.StopTime) stopTimes.get(endStopIndex);

            int duration = SECONDS_BETWEEN_STOPS * (endStopIndex - startStopIndex);
            int distance = DISTANCE_BETWEEN_STOPS * (endStopIndex - startStopIndex);

            return new DummyServiceModels.PublicTransitLeg(distance, duration, trip, departure, arrival);
        }

    }

}
