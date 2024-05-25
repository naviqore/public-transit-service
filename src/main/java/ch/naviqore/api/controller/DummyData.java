package ch.naviqore.api.controller;

import ch.naviqore.api.model.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DummyData {

    public static List<Stop> getStops() {
        List<Stop> dtos = new ArrayList<>();
        dtos.add(new Stop("Stop-1", "Train Station", new Coordinate(47.9, 8.0)));
        dtos.add(new Stop("Stop-2", "Bus Station", new Coordinate(47.95, 8.05)));
        dtos.add(new Stop("Stop-3", "Tram Station", new Coordinate(48.0, 8.1)));
        dtos.add(new Stop("Stop-4", "Central Station", new Coordinate(48.1, 8.0)));
        dtos.add(new Stop("Stop-5", "East Station", new Coordinate(47.9, 7.95)));
        dtos.add(new Stop("Stop-6", "West Station", new Coordinate(48.15, 8.1)));
        dtos.add(new Stop("Stop-7", "North Station", new Coordinate(48.05, 7.9)));
        dtos.add(new Stop("Stop-8", "South Station", new Coordinate(48.2, 8.05)));
        dtos.add(new Stop("Stop-9", "City Center", new Coordinate(47.95, 8.1)));
        dtos.add(new Stop("Stop-10", "Main Square", new Coordinate(48.0, 8.05)));
        dtos.add(new Stop("Stop-11", "University", new Coordinate(48.1, 7.95)));
        dtos.add(new Stop("Stop-12", "Airport", new Coordinate(47.9, 8.1)));
        dtos.add(new Stop("Stop-13", "Harbor", new Coordinate(48.2, 7.9)));
        dtos.add(new Stop("Stop-14", "Market Place", new Coordinate(48.05, 8.0)));
        dtos.add(new Stop("Stop-15", "Old Town", new Coordinate(47.95, 7.9)));
        dtos.add(new Stop("Stop-16", "Industrial Park", new Coordinate(48.0, 8.1)));
        dtos.add(new Stop("Stop-17", "Business District", new Coordinate(48.1, 8.05)));
        dtos.add(new Stop("Stop-18", "Residential Area", new Coordinate(48.2, 8.0)));
        dtos.add(new Stop("Stop-19", "Historic Site", new Coordinate(48.0, 7.95)));
        dtos.add(new Stop("Stop-20", "Tech Park", new Coordinate(47.9, 8.05)));
        dtos.add(new Stop("Stop-21", "City Hall", new Coordinate(48.1, 7.9)));
        dtos.add(new Stop("Stop-22", "Library", new Coordinate(48.2, 8.1)));
        dtos.add(new Stop("Stop-23", "Museum", new Coordinate(47.95, 8.0)));
        dtos.add(new Stop("Stop-24", "Stadium", new Coordinate(48.05, 8.1)));
        dtos.add(new Stop("Stop-25", "Aquarium", new Coordinate(48.0, 8.0)));
        dtos.add(new Stop("Stop-26", "Zoo", new Coordinate(48.1, 8.05)));
        dtos.add(new Stop("Stop-27", "Botanical Garden", new Coordinate(48.2, 7.95)));
        dtos.add(new Stop("Stop-28", "Concert Hall", new Coordinate(48.05, 7.9)));
        dtos.add(new Stop("Stop-29", "Exhibition Center", new Coordinate(48.0, 8.1)));
        dtos.add(new Stop("Stop-30", "Convention Center", new Coordinate(47.9, 8.0)));
        return dtos;
    }

    public static List<Route> getRoutes() {
        List<Route> dtos = new ArrayList<>();
        dtos.add(new Route("Route-1", "Train Route 1", "TR-1", "Train"));
        dtos.add(new Route("Route-2", "Bus Route 1", "B1", "Bus"));
        dtos.add(new Route("Route-3", "Tram Route 1", "T1", "Tram"));
        dtos.add(new Route("Route-4", "Train Route 2", "TR-2", "Train"));
        return dtos;
    }

    public static List<Stop> searchStops(String query, int limit, SearchType type) {
        List<Stop> stops = getStops();
        List<Stop> result = new ArrayList<>();
        if (type == SearchType.FUZZY || type == SearchType.CONTAINS) {
            for (Stop stop : stops) {
                if (stop.getName().toLowerCase().contains(query.toLowerCase())) {
                    result.add(stop);
                }
            }
        } else if (type == SearchType.STARTS_WITH) {
            for (Stop stop : stops) {
                if (stop.getName().toLowerCase().startsWith(query.toLowerCase())) {
                    result.add(stop);
                }
            }
        } else if (type == SearchType.EXACT) {
            for (Stop stop : stops) {
                if (stop.getName().toLowerCase().endsWith(query.toLowerCase())) {
                    result.add(stop);
                }
            }
        } else {
            for (Stop stop : stops) {
                if (stop.getName().equalsIgnoreCase(query)) {
                    result.add(stop);
                }
            }
        }
        if (result.size() > limit) {
            return result.subList(0, limit);
        }
        return result;
    }

    public static List<DistanceToStop> getNearestStops(double latitude, double longitude, int maxDistance, int limit) {
        List<Stop> stops = getStops();
        List<DistanceToStop> result = new ArrayList<>();
        for (Stop stop : stops) {
            double distance = approximateDistance(stop.getCoordinates(), new Coordinate(latitude, longitude));
            if (maxDistance > 0 && distance > maxDistance) {
                continue;
            }
            result.add(new DistanceToStop(stop, distance));
        }
        result.sort(Comparator.comparingDouble(DistanceToStop::getDistance));
        if (result.size() > limit) {
            return result.subList(0, limit);
        }
        return result;
    }

    public static double approximateDistance(Coordinate coord1, Coordinate coord2) {
        final double metersPerDegreeLat = 111320.0;
        final double lat1 = coord1.getLatitude();
        final double lon1 = coord1.getLongitude();
        final double lat2 = coord2.getLatitude();
        final double lon2 = coord2.getLongitude();

        // Convert latitude and longitude differences to meters
        double latDiff = (lat2 - lat1) * metersPerDegreeLat;
        double lonDiff = (lon2 - lon1) * metersPerDegreeLat * Math.cos(Math.toRadians((lat1 + lat2) / 2.0));

        // Use Pythagorean theorem to approximate distance
        return Math.sqrt(latDiff * latDiff + lonDiff * lonDiff);
    }

    public static Stop getStop(String stopId) {
        List<Stop> stops = getStops();
        for (Stop stop : stops) {
            if (stop.getId().equals(stopId)) {
                return stop;
            }
        }
        return null;
    }

    private static Connection buildSimpleDummyConnection(Stop from, Stop to, LocalDateTime departureTime) {
        ArrayList<Leg> legs = new ArrayList<>();
        legs.add(buildTripDummyLeg(from, to, departureTime));

        return new Connection(legs);
    }

    private static Connection buildTwoLegDummyConnection(Stop from, Stop to, LocalDateTime departureTime) {

        Stop stopBetween = getStopInBetweenStops(from, to);

        if (stopBetween == null) {
            return buildSimpleDummyConnection(from, to, departureTime);
        }

        ArrayList<Leg> legs = new ArrayList<>();
        Leg leg1 = buildTripDummyLeg(from, stopBetween, departureTime);
        legs.add(leg1);
        legs.add(buildTripDummyLeg(stopBetween, to, leg1.getArrivalTime()));

        return new Connection(legs);
    }

    private static Connection buildThreeLegDummyConnectionWithFootpath(Stop from, Stop to,
                                                                       LocalDateTime departureTime) {

        Stop stopBetween = getStopInBetweenStops(from, to);

        if (stopBetween == null) {
            return buildTwoLegDummyConnection(from, to, departureTime);
        }

        List<DistanceToStop> closestStops = getNearestStops(stopBetween.getCoordinates().getLatitude(),
                stopBetween.getCoordinates().getLongitude(), 5000, 10);

        Stop closestStop = null;

        for (DistanceToStop distanceToStop : closestStops) {
            if (distanceToStop.getStop().equals(from) || distanceToStop.getStop().equals(to) || distanceToStop.getStop()
                    .equals(stopBetween)) {
                continue;
            }
            closestStop = distanceToStop.getStop();
            break;
        }

        if (closestStop == null) {
            return buildTwoLegDummyConnection(from, to, departureTime);
        }

        ArrayList<Leg> legs = new ArrayList<>();
        Leg leg1 = buildTripDummyLeg(from, stopBetween, departureTime);
        legs.add(leg1);

        int footpathDistance = (int) approximateDistance(stopBetween.getCoordinates(), closestStop.getCoordinates());
        int footpathSpeed = 100; // meters per minute
        int footpathTravelTime = footpathDistance / footpathSpeed; // in minutes

        Leg leg2 = new Leg(stopBetween.getCoordinates(), closestStop.getCoordinates(), stopBetween, closestStop,
                LegType.WALK, leg1.getArrivalTime(), leg1.getArrivalTime().plusMinutes(footpathTravelTime), null);
        legs.add(leg2);
        legs.add(buildTripDummyLeg(closestStop, to, leg2.getArrivalTime()));

        return new Connection(legs);
    }

    private static Stop getStopInBetweenStops(Stop from, Stop to) {
        List<Stop> stops = getStops();
        // get all stops between from and to
        List<Stop> stopsBetween = new ArrayList<>();
        for (Stop stop : stops) {
            if (stop.equals(from) || stop.equals(to)) {
                continue;
            }
            double maxLat = Math.max(from.getCoordinates().getLatitude(), to.getCoordinates().getLatitude());
            double minLat = Math.min(from.getCoordinates().getLatitude(), to.getCoordinates().getLatitude());
            double maxLon = Math.max(from.getCoordinates().getLongitude(), to.getCoordinates().getLongitude());
            double minLon = Math.min(from.getCoordinates().getLongitude(), to.getCoordinates().getLongitude());
            if (stop.getCoordinates().getLatitude() > minLat && stop.getCoordinates()
                    .getLatitude() < maxLat && stop.getCoordinates().getLongitude() > minLon && stop.getCoordinates()
                    .getLongitude() < maxLon) {
                stopsBetween.add(stop);
                System.out.println("Stop between: " + stop.getName());
            }
        }

        if (stopsBetween.isEmpty()) {
            return null;
            //return stops.get((int) (Math.random() * stops.size()));
        }

        return stopsBetween.get((int) (Math.random() * stopsBetween.size()));
    }

    private static Leg buildTripDummyLeg(Stop from, Stop to, LocalDateTime departureTime) {
        double distance = approximateDistance(from.getCoordinates(), to.getCoordinates());
        // get route randomly
        List<Route> routes = getRoutes();
        Route route = routes.get((int) (Math.random() * routes.size()));
        int speed = 1000; // meters per minute
        if (route.getTransportMode().equals("Train")) {
            speed = 2000;
        }
        int travelTime = (int) (distance / speed); // in minutes
        LocalDateTime tripDepartureTime = departureTime.plusMinutes((int) (Math.random() * 20));
        LocalDateTime tripArrivalTime = tripDepartureTime.plusMinutes(travelTime);

        ArrayList<StopTime> stopTimes = new ArrayList<>();
        stopTimes.add(new StopTime(from, departureTime, tripDepartureTime));
        stopTimes.add(new StopTime(to, tripArrivalTime, tripArrivalTime));

        Trip trip = new Trip(to.getName(), route, stopTimes);

        return new Leg(from.getCoordinates(), to.getCoordinates(), from, to, LegType.ROUTE, departureTime,
                tripArrivalTime, trip);
    }

    public static List<Departure> getDepartures(String stopId, LocalDateTime departureTime, int limit,
                                                LocalDateTime untilDateTime) {

        Stop stop = getStop(stopId);

        if (departureTime == null) {
            departureTime = LocalDateTime.now();
        }

        List<Departure> departures = new ArrayList<>();
        List<Stop> stops = getStops();

        for (Stop targetStop : stops) {
            if (stop == targetStop) {
                continue;
            }

            int randomMinutes = (int) (Math.random() * 120);
            LocalDateTime tripDepartureTime = departureTime.plusMinutes(randomMinutes);
            Leg leg = buildTripDummyLeg(stop, targetStop, tripDepartureTime);
            Trip trip = leg.getTrip();

            if (untilDateTime != null && leg.getArrivalTime().isAfter(untilDateTime)) {
                continue;
            }

            departures.add(new Departure(trip.getStopTimes().getFirst(), trip));
        }

        // sort by departure time
        departures.sort(Comparator.comparing(departure -> departure.getStopTime().getDepartureTime()));

        if (departures.size() > limit) {
            return departures.subList(0, limit);
        }

        return departures;
    }

    public static List<Connection> getConnections(String fromStopId, String toStopId, LocalDateTime departureTime) {
        Stop from = getStop(fromStopId);
        Stop to = getStop(toStopId);

        if (departureTime == null) {
            departureTime = LocalDateTime.now();
        }

        List<Connection> connections = new ArrayList<>();
        connections.add(buildSimpleDummyConnection(from, to, departureTime));
        connections.add(buildTwoLegDummyConnection(from, to, departureTime));
        connections.add(buildThreeLegDummyConnectionWithFootpath(from, to, departureTime));

        return connections;
    }

    public static List<EarliestArrival> getIsolines(String fromStopId, LocalDateTime departureTime, int maxTravelTime) {
        Stop from = getStop(fromStopId);
        if (departureTime == null) {
            departureTime = LocalDateTime.now();
        }

        LocalDateTime untilDateTime = null;
        if (maxTravelTime < Integer.MAX_VALUE) {
            untilDateTime = departureTime.plusMinutes(maxTravelTime);
        }

        List<Stop> stops = getStops();
        List<EarliestArrival> isolines = new ArrayList<>();

        for (Stop targetStop : stops) {
            if (from == targetStop) {
                continue;
            }
            Connection connection = buildSimpleDummyConnection(from, targetStop, departureTime);

            Leg lastLeg = connection.getLegs().get(connection.getLegs().size() - 1);
            if (untilDateTime != null && lastLeg.getArrivalTime().isAfter(untilDateTime)) {
                continue;
            }

            isolines.add(new EarliestArrival(targetStop, lastLeg.getArrivalTime(), connection));
        }

        return isolines;
    }

}
