package ch.naviqore.api.controller;

import ch.naviqore.api.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;

public class DummyData {

    private static final Logger logger = LoggerFactory.getLogger(DummyData.class);
    static Random random = new Random();

    public static List<Stop> getStops() {
        List<Stop> dtos = new ArrayList<>();
        dtos.add(new Stop("Stop-1", "Zürich, Klinik Hirslanden", new Coordinate(47.35189719, 8.576602294)));
        dtos.add(new Stop("Stop-2", "Brüttisellen, Gemeindehaus", new Coordinate(47.42036924, 8.628830345)));
        dtos.add(new Stop("Stop-3", "Hausen a.A., Post A", new Coordinate(47.24489999, 8.532728576)));
        dtos.add(new Stop("Stop-4", "Zürich, Bahnhof Stadelhofen", new Coordinate(47.36616091, 8.5472723)));
        dtos.add(new Stop("Stop-5", "Spital Zollikerberg", new Coordinate(47.34735681, 8.596634725)));
        dtos.add(new Stop("Stop-6", "Zürich, Central Polybahn", new Coordinate(47.37652769, 8.544352776)));
        dtos.add(new Stop("Stop-7", "Uster, Buchholz", new Coordinate(47.35874961, 8.729172162)));
        dtos.add(new Stop("Stop-8", "Zürich, Triemli", new Coordinate(47.3680227, 8.495682053)));
        dtos.add(new Stop("Stop-9", "Winterthur, Hauptbahnhof", new Coordinate(47.49938025, 8.723908035)));
        dtos.add(new Stop("Stop-10", "Zürich Flughafen, Bahnhof", new Coordinate(47.45027729, 8.56405283)));
        dtos.add(new Stop("Stop-11", "Zürich, Schwamendingerplatz", new Coordinate(47.40487989, 8.571481897)));
        dtos.add(new Stop("Stop-12", "Roswiesen", new Coordinate(47.40295252, 8.576979587)));
        dtos.add(new Stop("Stop-13", "Siemens", new Coordinate(47.37846819, 8.494002204)));
        dtos.add(new Stop("Stop-14", "Stauffacher", new Coordinate(47.37342517, 8.529252096)));
        dtos.add(new Stop("Stop-15", "Sternen Oerlikon", new Coordinate(47.41007188, 8.546230254)));
        dtos.add(new Stop("Stop-16", "Zoo", new Coordinate(47.38157041, 8.571553762)));
        dtos.add(new Stop("Stop-17", "Universität Irchel", new Coordinate(47.39616056, 8.544828883)));
        dtos.add(new Stop("Stop-18", "Benglen, Gerlisbrunnen", new Coordinate(47.36092808, 8.632872764)));
        dtos.add(new Stop("Stop-19", "Winterthur, Grüzenstrasse", new Coordinate(47.49825747, 8.744587252)));
        dtos.add(new Stop("Stop-20", "Zürich, Bahnhof Wollishofen", new Coordinate(47.34825762, 8.533294514)));
        dtos.add(new Stop("Stop-21", "Elsau, Melcher", new Coordinate(47.50171676, 8.787230279)));
        dtos.add(new Stop("Stop-22", "Wetzikon ZH, Walfershausen", new Coordinate(47.32205504, 8.797336326)));
        dtos.add(new Stop("Stop-23", "Hombrechtikon, Tobel", new Coordinate(47.25379065, 8.780986988)));
        dtos.add(new Stop("Stop-24", "Mönchaltorf, Langenmatt", new Coordinate(47.315526886, 8.7209782185)));
        dtos.add(new Stop("Stop-25", "Hinwil, Girenbad", new Coordinate(47.30950315, 8.87194141)));
        dtos.add(new Stop("Stop-26", "Zürich, Polyterrasse ETH", new Coordinate(47.37685009, 8.546391951)));
        dtos.add(new Stop("Stop-27", "Pfäffikon ZH, Humbel", new Coordinate(47.38172856, 8.800857722)));
        dtos.add(new Stop("Stop-28", "Benken ZH, Dorf", new Coordinate(47.6529254, 8.653300453)));
        dtos.add(new Stop("Stop-29", "Winkel, Oberrüti", new Coordinate(47.47994371, 8.563073666)));
        dtos.add(new Stop("Stop-30", "Flaach, Wesenplatz", new Coordinate(47.57530917, 8.609651314)));
        dtos.add(new Stop("Stop-31", "Laupen ZH, Schulhaus", new Coordinate(47.26536213, 8.929370706)));
        dtos.add(new Stop("Stop-32", "Embrach, Dreispitz", new Coordinate(47.50995745, 8.593858931)));
        dtos.add(new Stop("Stop-33", "Brütten, Zentrum", new Coordinate(47.47198977, 8.676189527)));
        dtos.add(new Stop("Stop-34", "Mettmenstetten, Bahnhof", new Coordinate(47.24308265, 8.458671464)));
        dtos.add(new Stop("Stop-35", "Baar, Bahnmatt", new Coordinate(47.19659729, 8.523862204)));
        dtos.add(new Stop("Stop-36", "Rümlang, Bahnhof", new Coordinate(47.45400701, 8.532611795)));
        dtos.add(new Stop("Stop-37", "Bachs, Weiherbach", new Coordinate(47.5193738, 8.44891576)));
        dtos.add(new Stop("Stop-38", "Dachsen, Lindenstrasse", new Coordinate(47.66353213, 8.620538895)));
        dtos.add(new Stop("Stop-39", "Russikon, Kirche", new Coordinate(47.39620313, 8.773108763)));
        dtos.add(new Stop("Stop-40", "Weisslingen, Widum", new Coordinate(47.43468736, 8.76346984)));
        dtos.add(new Stop("Stop-41", "Horgen, Stocker", new Coordinate(47.26150932, 8.58926854)));
        dtos.add(new Stop("Stop-42", "Hirzel, Strickler", new Coordinate(47.22884668, 8.601844954)));
        dtos.add(new Stop("Stop-43", "Sitzberg, Sternen", new Coordinate(47.4234933, 8.912563227)));
        dtos.add(new Stop("Stop-44", "Buch am Irchel, Unterbuch", new Coordinate(47.54997739, 8.61878718)));
        dtos.add(new Stop("Stop-45", "Oetwil am See, Bergstrasse", new Coordinate(47.26976326, 8.718051019)));
        dtos.add(new Stop("Stop-46", "Pfungen, Eskimo", new Coordinate(47.51937987, 8.637202643)));
        dtos.add(new Stop("Stop-47", "Wallisellen, Bahnhof", new Coordinate(47.41268592, 8.592933666)));
        dtos.add(new Stop("Stop-48", "Maur, See", new Coordinate(47.33994896, 8.677968191)));
        dtos.add(new Stop("Stop-49", "Meilen, Friedhof", new Coordinate(47.27177473, 8.641424725)));
        dtos.add(new Stop("Stop-50", "Stäfa, Schützenhaus Wanne", new Coordinate(47.25471134, 8.720611217)));

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
        return getNearestStops(getStops(), latitude, longitude, maxDistance, limit);
    }

    private static List<DistanceToStop> getNearestStops(List<Stop> stops, double latitude, double longitude,
                                                        int maxDistance, int limit) {
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

        Stop stopBetween = getStopInBetweenStops(from, to, 2);

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

        Stop stopBetween = getStopInBetweenStops(from, to, 2);

        if (stopBetween == null) {
            return buildTwoLegDummyConnection(from, to, departureTime);
        }

        List<DistanceToStop> closestStops = getNearestStops(stopBetween.getCoordinates().getLatitude(),
                stopBetween.getCoordinates().getLongitude(), Integer.MAX_VALUE, 4);

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
        Leg leg2 = buildFootpathLeg(stopBetween, closestStop, leg1.getArrivalTime());
        legs.add(leg2);
        legs.add(buildTripDummyLeg(closestStop, to, leg2.getArrivalTime()));

        return new Connection(legs);
    }

    private static Leg buildFootpathLeg(Stop from, Stop to, LocalDateTime departureTime) {
        int footpathDistance = (int) approximateDistance(from.getCoordinates(), to.getCoordinates());
        int footpathSpeed = 100; // meters per minute
        int footpathTravelTime = footpathDistance / footpathSpeed; // in minutes

        return new Leg(from.getCoordinates(), to.getCoordinates(), from, to, LegType.WALK, departureTime,
                departureTime.plusMinutes(footpathTravelTime), null);
    }

    private static Stop getStopInBetweenStops(Stop from, Stop to, int randomness) {
        return getStopInBetweenStops(from, to, randomness, List.of(from, to));
    }

    private static Stop getStopInBetweenStops(Stop from, Stop to, int randomness, List<Stop> exclusionStops) {

        // get coordinate of the center of the two stops
        double centerLat = (from.getCoordinates().getLatitude() + to.getCoordinates().getLatitude()) / 2;
        double centerLon = (from.getCoordinates().getLongitude() + to.getCoordinates().getLongitude()) / 2;
        Coordinate center = new Coordinate(centerLat, centerLon);

        List<DistanceToStop> closestStops = getNearestStops(center.getLatitude(), center.getLongitude(),
                Integer.MAX_VALUE, 3 + randomness);

        // random sort to get different results
        closestStops.sort((a, b) -> {
            if (a.getStop().equals(b.getStop()) && a.getDistance() == b.getDistance()) {
                return 0;
            }
            return random.nextInt(3) < 2 ? -1 : 1;
        });

        for (DistanceToStop distanceToStop : closestStops) {
            if (exclusionStops.contains(distanceToStop.getStop())) {
                continue;
            }
            return distanceToStop.getStop();
        }

        return null;
    }

    private static Leg buildTripDummyLeg(Stop from, Stop to, LocalDateTime departureTime) {
        double distance = approximateDistance(from.getCoordinates(), to.getCoordinates());
        // get route randomly
        List<Route> routes = getRoutes();
        int routeIndex = random.nextInt(routes.size());
        Route route = routes.get(routeIndex);

        int speed = 1000; // meters per minute
        if (route.getTransportMode().equals("Train")) {
            speed = 2000;
        }
        int travelTime = (int) (distance / speed); // in minutes
        int departureOffset = random.nextInt(20);
        LocalDateTime tripDepartureTime = departureTime.plusMinutes(departureOffset);
        LocalDateTime tripArrivalTime = tripDepartureTime.plusMinutes(travelTime);

        ArrayList<StopTime> stopTimes = new ArrayList<>();
        stopTimes.add(new StopTime(from, tripDepartureTime, tripDepartureTime));
        stopTimes.add(new StopTime(to, tripArrivalTime, tripArrivalTime));

        Trip trip = new Trip(to.getName(), route, stopTimes);

        return new Leg(from.getCoordinates(), to.getCoordinates(), from, to, LegType.ROUTE, tripDepartureTime,
                tripArrivalTime, trip);
    }

    public static List<Departure> getDepartures(String stopId, LocalDateTime departureTime, int limit,
                                                LocalDateTime untilDateTime) {

        logger.info("Getting departures for stop: " + stopId + " at " + departureTime + " with limit " + limit);

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

            LocalDateTime tripDepartureTime = departureTime.plusMinutes(random.nextInt(120));
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

        logger.info("Getting connections from " + from.getName() + " to " + to.getName() + " at " + departureTime);

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
        if (from == null) {
            throw new IllegalArgumentException("Stop not found");
        }
        if (departureTime == null) {
            departureTime = LocalDateTime.now();
        }

        LocalDateTime untilDateTime = null;
        if (maxTravelTime < Integer.MAX_VALUE) {
            untilDateTime = departureTime.plusMinutes(maxTravelTime);
        }

        List<Stop> stops = getStops();
        List<Stop> spawnStops = new ArrayList<>();
        stops.remove(from);

        List<EarliestArrival> isolines = new ArrayList<>();
        HashMap<Stop, Leg> earliestArrivals = new HashMap<>();

        // find nearest stop and walk there by foot
        List<DistanceToStop> closestStops = getNearestStops(from.getCoordinates().getLatitude(),
                from.getCoordinates().getLongitude(), Integer.MAX_VALUE, 10);
        Stop nearestStop = closestStops.get(0).getStop();
        spawnStops.add(nearestStop);
        stops.remove(nearestStop);
        earliestArrivals.put(nearestStop, buildFootpathLeg(from, nearestStop, departureTime));

        // pick 3 random stops to add trip to
        for (int i = 0; i < 3; i++) {
            int randomIndex = random.nextInt(closestStops.size());
            DistanceToStop targetStopDistance = closestStops.get(randomIndex);
            Stop targetStop = targetStopDistance.getStop();
            stops.remove(targetStop);
            spawnStops.add(targetStop);
            earliestArrivals.put(targetStop, buildTripDummyLeg(from, targetStop, departureTime));
        }

        while (!stops.isEmpty()) {
            Stop spawnStop;
            if (spawnStops.isEmpty()) {
                spawnStop = from;
            } else {
                spawnStop = spawnStops.removeFirst();
            }
            int maxConnections = stops.size() / 3;
            int numConnections = random.nextInt(maxConnections + 2);

            List<DistanceToStop> nearestStops = getNearestStops(stops, spawnStop.getCoordinates().getLatitude(),
                    spawnStop.getCoordinates().getLongitude(), Integer.MAX_VALUE, 5 + numConnections);

            // shuffle to get not only closest stops
            nearestStops.sort((a, b) -> {
                if (a.getStop().equals(b.getStop()) && a.getDistance() == b.getDistance()) {
                    return 0;
                }
                return random.nextInt(3) < 2 ? -1 : 1;
            });

            LocalDateTime spawnDepartureTime = earliestArrivals.get(spawnStop).getArrivalTime();

            for (int i = 0; i < numConnections; i++) {
                Stop targetStop = nearestStops.get(i).getStop();
                stops.remove(targetStop);
                spawnStops.add(targetStop);
                Leg leg = buildTripDummyLeg(spawnStop, targetStop, spawnDepartureTime);
                if (untilDateTime != null && leg.getArrivalTime().isAfter(untilDateTime)) {
                    continue;
                }
                earliestArrivals.put(targetStop, leg);
            }

        }

        // build connections for all stops
        for (Map.Entry<Stop, Leg> entry : earliestArrivals.entrySet()) {
            Stop targetStop = entry.getKey();
            Leg lastLeg = entry.getValue();
            List<Leg> legs = new ArrayList<>(List.of(lastLeg));

            while (lastLeg.getFromStop() != from) {
                lastLeg = earliestArrivals.get(lastLeg.getFromStop());
                legs.add(lastLeg);
            }

            // sort legs by departure time
            legs.sort(Comparator.comparing(Leg::getDepartureTime));
            isolines.add(new EarliestArrival(targetStop, legs.getLast().getArrivalTime(), new Connection(legs)));
        }

        return isolines;
    }

}
