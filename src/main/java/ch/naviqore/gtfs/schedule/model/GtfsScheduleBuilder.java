package ch.naviqore.gtfs.schedule.model;

import ch.naviqore.gtfs.schedule.type.*;
import ch.naviqore.utils.cache.ValueObjectCache;
import ch.naviqore.utils.spatial.GeoCoordinate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/**
 * Implements a builder pattern for constructing instances of {@link GtfsSchedule}. This builder helps assemble a GTFS
 * schedule by adding components like agencies, stops, routes, trips, and calendars in a controlled and consistent
 * manner.
 * <p>
 * Use {@link GtfsSchedule#builder()} to obtain an instance.
 *
 * @author munterfi
 */
@NoArgsConstructor(access = AccessLevel.PACKAGE)
@Slf4j
public class GtfsScheduleBuilder {

    private final ValueObjectCache<LocalDate> localDateCache = new ValueObjectCache<>();
    private final ValueObjectCache<ServiceDayTime> serviceDayTimeCache = new ValueObjectCache<>();
    private final ValueObjectCache<String> stringCache = new ValueObjectCache<>();
    private final Map<String, Agency> agencies = new HashMap<>();
    private final Map<String, Calendar> calendars = new HashMap<>();
    private final Map<String, Stop> stops = new HashMap<>();
    private final Map<String, Route> routes = new HashMap<>();
    private final Map<String, Trip> trips = new HashMap<>();
    private final Map<String, List<Stop>> parents = new HashMap<>();

    private boolean built = false;

    public GtfsScheduleBuilder addAgency(String id, String name, String url, String timezone) {
        checkNotBuilt();
        if (agencies.containsKey(id)) {
            throw new IllegalArgumentException("Agency " + id + " already exists");
        }
        log.debug("Adding agency {}", id);
        agencies.put(id, new Agency(id, name, url, timezone));
        return this;
    }

    public GtfsScheduleBuilder addStop(String id, String name, double lat, double lon) {
        addStop(id, name, lat, lon, "", AccessibilityInformation.UNKNOWN);
        return this;
    }

    public GtfsScheduleBuilder addStop(String id, String name, double lat, double lon, String parentStopId,
                                       AccessibilityInformation wheelchairBoarding) {
        checkNotBuilt();
        if (stops.containsKey(id)) {
            throw new IllegalArgumentException("Stop " + id + " already exists");
        }
        log.debug("Adding stop {}", id);
        Stop stop = new Stop(id, name, new GeoCoordinate(lat, lon), wheelchairBoarding);

        // only add stop id if it is not a blank string
        if (!parentStopId.isEmpty()) {
            parents.computeIfAbsent(parentStopId, ignored -> new ArrayList<>()).add(stop);
        }

        stops.put(id, stop);
        return this;
    }

    public GtfsScheduleBuilder addRoute(String id, String agencyId, String shortName, String longName, RouteType type) {
        checkNotBuilt();
        if (routes.containsKey(id)) {
            throw new IllegalArgumentException("Route " + id + " already exists");
        }
        Agency agency = agencies.get(agencyId);
        if (agency == null) {
            throw new IllegalArgumentException("Agency " + agencyId + " does not exist");
        }
        log.debug("Adding route {}", id);
        routes.put(id, new Route(id, agency, shortName, longName, type));
        return this;
    }

    public GtfsScheduleBuilder addCalendar(String id, EnumSet<DayOfWeek> serviceDays, LocalDate startDate,
                                           LocalDate endDate) {
        checkNotBuilt();
        if (calendars.containsKey(id)) {
            throw new IllegalArgumentException("Calendar " + id + " already exists");
        }
        log.debug("Adding calendar {}", id);
        calendars.put(id,
                new Calendar(id, serviceDays, localDateCache.getOrAdd(startDate), localDateCache.getOrAdd(endDate)));
        return this;
    }

    public GtfsScheduleBuilder addCalendarDate(String calendarId, LocalDate date, ExceptionType type) {
        checkNotBuilt();
        Calendar calendar = calendars.get(calendarId);
        if (calendar == null) {
            throw new IllegalArgumentException("Calendar " + calendarId + " does not exist");
        }
        log.debug("Adding calendar {}-{}", calendarId, date);
        CalendarDate calendarDate = new CalendarDate(calendar, localDateCache.getOrAdd(date), type);
        calendar.addCalendarDate(calendarDate);
        return this;
    }

    public GtfsScheduleBuilder addTrip(String id, String routeId, String serviceId, String headSign) {
        return addTrip(id, routeId, serviceId, headSign, AccessibilityInformation.UNKNOWN, TripBikeInformation.UNKNOWN);
    }

    public GtfsScheduleBuilder addTrip(String id, String routeId, String serviceId, String headSign, AccessibilityInformation wheelchairAccessible, TripBikeInformation bikesAllowed) {
        checkNotBuilt();
        if (trips.containsKey(id)) {
            throw new IllegalArgumentException("Trip " + id + " already exists");
        }
        Route route = routes.get(routeId);
        if (route == null) {
            throw new IllegalArgumentException("Route " + routeId + " does not exist");
        }
        Calendar calendar = calendars.get(serviceId);
        if (calendar == null) {
            throw new IllegalArgumentException("Calendar " + serviceId + " does not exist");
        }
        log.debug("Adding trip {}", id);
        Trip trip = new Trip(id, route, calendar, stringCache.getOrAdd(headSign), wheelchairAccessible, bikesAllowed);
        route.addTrip(trip);
        trips.put(id, trip);
        calendar.addTrip(trip);
        return this;
    }

    public GtfsScheduleBuilder addStopTime(String tripId, String stopId, ServiceDayTime arrival,
                                           ServiceDayTime departure) {
        checkNotBuilt();
        Trip trip = trips.get(tripId);
        if (trip == null) {
            throw new IllegalArgumentException("Trip " + tripId + " does not exist");
        }
        Stop stop = stops.get(stopId);
        if (stop == null) {
            throw new IllegalArgumentException("Stop " + stopId + " does not exist");
        }
        log.debug("Adding stop time at {} to trip {} ({}-{})", stopId, tripId, arrival, departure);
        StopTime stopTime = new StopTime(stop, trip, serviceDayTimeCache.getOrAdd(arrival),
                serviceDayTimeCache.getOrAdd(departure));
        stop.addStopTime(stopTime);
        trip.addStopTime(stopTime);
        return this;
    }

    public GtfsScheduleBuilder addTransfer(String fromStopId, String toStopId, TransferType transferType,
                                           @Nullable Integer minTransferTime) {
        checkNotBuilt();
        Stop fromStop = stops.get(fromStopId);
        if (fromStop == null) {
            throw new IllegalArgumentException("Stop " + fromStopId + " does not exist");
        }
        Stop toStop = stops.get(toStopId);
        if (toStop == null) {
            throw new IllegalArgumentException("Stop " + toStopId + " does not exist");
        }
        if (transferType == TransferType.MINIMUM_TIME && minTransferTime == null) {
            throw new IllegalArgumentException(
                    "Minimal transfer time is not present for transfer of type " + transferType.name() + " from stop " + fromStopId + " to stop " + toStopId);
        }
        log.debug("Adding transfer {}-{} of type {} {}", fromStopId, toStopId, transferType, minTransferTime);
        fromStop.addTransfer(new Transfer(fromStop, toStop, transferType, minTransferTime));
        return this;
    }

    /**
     * Constructs and returns a {@link GtfsSchedule} using the current builder state.
     * <p>
     * This method finalizes the schedule and initializes all components. After this method is called, the builder is
     * cleared and cannot be used to build another schedule without being reset.
     *
     * @return The constructed {@link GtfsSchedule}.
     * @throws IllegalStateException if the builder has already built a schedule.
     */
    public GtfsSchedule build() {
        checkNotBuilt();

        log.info("Building schedule with {} stops, {} routes and {} trips", stops.size(), routes.size(), trips.size());
        setParentChildrenStopRelations();

        // initialize: make immutable and resize arrays to capacity
        trips.values().parallelStream().forEach(Initializable::initialize);
        stops.values().parallelStream().forEach(Initializable::initialize);
        routes.values().parallelStream().forEach(Initializable::initialize);
        calendars.values().parallelStream().forEach(Initializable::initialize);

        GtfsSchedule schedule = new GtfsSchedule(agencies, calendars, stops, routes, trips);
        clear();
        built = true;

        return schedule;
    }

    private void setParentChildrenStopRelations() {
        parents.forEach((parentId, children) -> {
            Stop parent = stops.get(parentId);

            if (parent == null) {
                throw new IllegalStateException("Parent stop " + parentId + " for children " + children.stream()
                        .map(Stop::getId)
                        .toList() + " does not exist.");
            }

            parent.setChildren(children);
            children.forEach(child -> child.setParent(parent));
        });
    }

    /**
     * Resets the builder to its initial state, allowing it to be reused.
     */
    public void reset() {
        log.debug("Resetting builder");
        clear();
        built = false;
    }

    private void clear() {
        log.debug("Clearing cache and maps of the builder");
        localDateCache.clear();
        serviceDayTimeCache.clear();
        agencies.clear();
        calendars.clear();
        stops.clear();
        routes.clear();
        trips.clear();
    }

    private void checkNotBuilt() {
        if (built) {
            throw new IllegalStateException("Cannot modify builder after build() has been called.");
        }
    }
}
