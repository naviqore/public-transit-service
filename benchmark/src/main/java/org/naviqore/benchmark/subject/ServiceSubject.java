package org.naviqore.benchmark.subject;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.management.ThreadMXBean;
import org.jspecify.annotations.Nullable;
import org.naviqore.benchmark.workload.Query;
import org.naviqore.gtfs.schedule.GtfsScheduleReader;
import org.naviqore.service.*;
import org.naviqore.service.config.ConnectionQueryConfig;
import org.naviqore.service.config.ServiceConfig;
import org.naviqore.service.exception.ConnectionRoutingException;
import org.naviqore.service.exception.StopNotFoundException;
import org.naviqore.timetable.model.Timetable;
import org.naviqore.utils.spatial.GeoCoordinate;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The subject of the version it is built with: reads a GTFS feed with {@link GtfsScheduleReader}, builds the public
 * transit service on it with {@link PublicTransitServiceFactory} in its default configuration, and runs queries through
 * the {@link PublicTransitService}, as an application does.
 * <p>
 * Answers are text, so that the answers of two versions compare line by line: a connection is its departure and
 * arrival, then each of its legs with its stops, times, trip, distance and duration.
 */
public class ServiceSubject implements Subject {

    private static final ThreadMXBean THREADS = (ThreadMXBean) ManagementFactory.getThreadMXBean();

    /**
     * The offset of a coordinate near a stop from the stop, in degrees of latitude and of longitude: a few hundred
     * meters, within walking distance but off the stop.
     */
    private static final double NEAR_OFFSET = 0.002;

    /**
     * The loggers of the libraries, which log every query: noise among thousands of queries, and time measured.
     */
    private static final List<String> LIBRARY_LOGGERS = List.of("org.naviqore.gtfs", "org.naviqore.raptor",
            "org.naviqore.service", "org.naviqore.timetable", "org.naviqore.utils");

    private static final int MINUTES = 60;
    private static final Map<String, TravelMode> TRAVEL_MODES = Map.of("bus", TravelMode.BUS, "tram", TravelMode.TRAM,
            "rail", TravelMode.RAIL, "ship", TravelMode.SHIP, "subway", TravelMode.SUBWAY, "aerial",
            TravelMode.AERIAL_LIFT, "funicular", TravelMode.FUNICULAR);

    private final ListAppender<ILoggingEvent> logEvents = new ListAppender<>();
    private @Nullable Timetable timetable;
    private @Nullable PublicTransitService service;

    private static Logger rootLogger() {
        return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }

    private static ConnectionQueryConfig queryConfig(String configuration) {
        ConnectionQueryConfig.ConnectionQueryConfigBuilder builder = ConnectionQueryConfig.builder();
        EnumSet<TravelMode> travelModes = EnumSet.noneOf(TravelMode.class);
        for (String option : configuration.split("\\+")) {
            switch (option) {
                case "default" -> {
                }
                case "wheelchair" -> builder.wheelchairAccessible(true);
                case "bike" -> builder.bikeAllowed(true);
                case "transfers0" -> builder.maximumTransfers(0);
                case "transfers1" -> builder.maximumTransfers(1);
                case "walk5" -> builder.maximumWalkDuration(5 * MINUTES);
                case "mintransfer5" -> builder.minimumTransferDuration(5 * MINUTES);
                case "travel60" -> builder.maximumTravelDuration(60 * MINUTES);
                case "window60" -> builder.timeWindowDuration(60 * MINUTES);
                default -> {
                    TravelMode travelMode = TRAVEL_MODES.get(option);
                    if (travelMode == null) {
                        throw new IllegalArgumentException("Unknown option of a query configuration: " + option);
                    }
                    travelModes.add(travelMode);
                }
            }
        }
        if (!travelModes.isEmpty()) {
            builder.travelModes(travelModes);
        }

        return builder.build();
    }

    private static String text(Connection connection) {
        StringBuilder text = new StringBuilder(
                connection.getDepartureTime() + " -> " + connection.getArrivalTime() + " |");
        for (Leg leg : connection.getLegs()) {
            text.append(' ').append(text(leg)).append(" ;");
        }

        return text.toString();
    }

    private static String text(Leg leg) {
        String text = switch (leg) {
            case PublicTransitLeg ride -> {
                Trip trip = ride.getTrip();
                StopTime departure = ride.getDeparture();
                StopTime arrival = ride.getArrival();
                yield String.join(" ", "PT", trip.getId(), trip.getRoute().getId(),
                        departure.getStop().getId() + "@" + departure.getDepartureTime(), "->",
                        arrival.getStop().getId() + "@" + arrival.getArrivalTime());
            }
            case Transfer transfer ->
                    String.join(" ", "TR", transfer.getSourceStop().getId() + "@" + transfer.getDepartureTime(), "->",
                            transfer.getTargetStop().getId() + "@" + transfer.getArrivalTime());
            case Walk walk -> String.join(" ", "WA", walk.getWalkType().name(),
                    walk.getSourceLocation() + "@" + walk.getDepartureTime(), "->",
                    walk.getTargetLocation() + "@" + walk.getArrivalTime(),
                    "stop=" + walk.getStop().map(Stop::getId).orElse("-"));
            default -> "?? " + leg.getLegType();
        };

        return text + " d=" + leg.getDistance() + " t=" + leg.getDuration();
    }

    private static String failure(Throwable throwable) {
        return "EXCEPTION " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
    }

    @Override
    public void read(String feed) {
        // record what the libraries log while the feed is read and the service is built, see build()
        logEvents.list.clear();
        logEvents.start();
        rootLogger().addAppender(logEvents);
        try {
            timetable = new GtfsScheduleReader().read(feed);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void build() {
        Timetable read = Objects.requireNonNull(timetable, "Read a feed first");
        try {
            service = new PublicTransitServiceFactory(
                    ServiceConfig.builder().timetableRepository(() -> read).build()).create();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while building the service", e);
        } finally {
            // the log of the queries that follow is not of interest
            rootLogger().detachAppender(logEvents);
            logEvents.stop();
            LIBRARY_LOGGERS.forEach(name -> ((Logger) LoggerFactory.getLogger(name)).setLevel(Level.WARN));
        }
    }

    @Override
    public List<String> log() {
        List<String> lines = new ArrayList<>(logEvents.list.size());
        for (ILoggingEvent event : logEvents.list) {
            if (event.getLevel().isGreaterOrEqual(Level.INFO)) {
                String logger = event.getLoggerName().substring(event.getLoggerName().lastIndexOf('.') + 1);
                lines.add(event.getTimeStamp() + "\t" + logger + "\t" + event.getFormattedMessage());
            }
        }

        return lines;
    }

    @Override
    public List<String> validity() {
        Validity validity = service().getValidity();
        return List.of(validity.getStartDate().toString(), validity.getEndDate().toString());
    }

    @Override
    public String zone() {
        Map<ZoneId, Long> routesByZone = Objects.requireNonNull(timetable, "Read a feed first")
                .getRoutes()
                .values()
                .stream()
                .collect(Collectors.groupingBy(route -> route.getAgency().timezone(), Collectors.counting()));

        // of equally frequent zones the first by name, so that every version picks the same
        return routesByZone.entrySet()
                .stream()
                .max(Map.Entry.<ZoneId, Long>comparingByValue()
                        .thenComparing(entry -> entry.getKey().getId(), Comparator.reverseOrder()))
                .map(entry -> entry.getKey().getId())
                .orElseThrow(() -> new IllegalStateException("The timetable has no routes"));
    }

    @Override
    public String stopByName(String name) {
        List<Stop> stops = service().getStops(name, SearchType.STARTS_WITH, StopSortStrategy.RELEVANCE);
        if (stops.isEmpty()) {
            return service().getRandomStop(new Random(name.hashCode())).getId();
        }

        return stops.stream().filter(stop -> stop.getName().equals(name)).findFirst().orElse(stops.getFirst()).getId();
    }

    @Override
    public List<String> randomStops(int count, long seed) {
        Random random = new Random(seed);
        List<String> stopIds = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            stopIds.add(service().getRandomStop(random).getId());
        }

        return stopIds;
    }

    @Override
    public List<String> answer(List<String> fields) {
        Query query = Query.decode(fields);
        try {
            return switch (query.kind()) {
                case CONNECTIONS -> connections(query).stream().map(ServiceSubject::text).toList();
                case ISOLINES -> isolines(query).entrySet()
                        .stream()
                        .map(entry -> entry.getKey().getId() + " " + text(entry.getValue()))
                        .sorted()
                        .toList();
            };
        } catch (Exception | AssertionError e) {
            return List.of(failure(e));
        }
    }

    @Override
    public long[] time(List<String> fields) {
        Query query = Query.decode(fields);
        long allocated = THREADS.getCurrentThreadAllocatedBytes();
        long cpuTime = THREADS.getCurrentThreadCpuTime();
        long started = System.nanoTime();
        int answers;
        try {
            answers = switch (query.kind()) {
                case CONNECTIONS -> connections(query).size();
                case ISOLINES -> isolines(query).size();
            };
        } catch (Exception | AssertionError e) {
            answers = -1;
        }
        long wallTime = System.nanoTime() - started;
        cpuTime = THREADS.getCurrentThreadCpuTime() - cpuTime;
        allocated = THREADS.getCurrentThreadAllocatedBytes() - allocated;

        return new long[]{wallTime, cpuTime, allocated, answers};
    }

    private List<Connection> connections(Query query) throws StopNotFoundException, ConnectionRoutingException {
        Query.Location target = Objects.requireNonNull(query.target());
        TimeType timeType = TimeType.valueOf(query.timeType().name());
        ConnectionQueryConfig config = queryConfig(query.configuration());
        boolean fromStop = query.source().type() == Query.Location.Type.STOP;
        boolean toStop = target.type() == Query.Location.Type.STOP;

        if (fromStop && toStop) {
            return service().getConnections(stop(query.source()), stop(target), query.time(), timeType, config);
        } else if (fromStop) {
            return service().getConnections(stop(query.source()), near(target), query.time(), timeType, config);
        } else if (toStop) {
            return service().getConnections(near(query.source()), stop(target), query.time(), timeType, config);
        }

        return service().getConnections(near(query.source()), near(target), query.time(), timeType, config);
    }

    private Map<Stop, Connection> isolines(Query query) throws StopNotFoundException, ConnectionRoutingException {
        TimeType timeType = TimeType.valueOf(query.timeType().name());
        ConnectionQueryConfig config = queryConfig(query.configuration());

        return query.source().type() == Query.Location.Type.STOP ? service().getIsolines(stop(query.source()),
                query.time(), timeType, config) : service().getIsolines(near(query.source()), query.time(), timeType,
                config);
    }

    private Stop stop(Query.Location location) throws StopNotFoundException {
        return service().getStopById(location.stopId());
    }

    private GeoCoordinate near(Query.Location location) throws StopNotFoundException {
        GeoCoordinate coordinate = stop(location).getCoordinate();
        return new GeoCoordinate(coordinate.latitude() + NEAR_OFFSET, coordinate.longitude() + NEAR_OFFSET);
    }

    private PublicTransitService service() {
        return Objects.requireNonNull(service, "Build the service first");
    }

}
