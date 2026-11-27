package org.naviqore.gtfs.schedule.model;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.naviqore.gtfs.schedule.type.ExceptionType;
import org.naviqore.gtfs.schedule.type.LocationType;
import org.naviqore.gtfs.schedule.type.RouteType;
import org.naviqore.timetable.type.*;
import org.naviqore.utils.cache.ValueObjectCache;
import org.naviqore.utils.spatial.GeoCoordinate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
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

    private static boolean linksTrips(TransferType transferType) {
        return transferType == TransferType.IN_SEAT || transferType == TransferType.IN_SEAT_FORBIDDEN;
    }

    /**
     * Gets the stop a trip starts or ends at, as its stop times are sorted once built: by departure, and those that
     * depart at the same time in the order they were added.
     */
    private static Stop getTerminalStop(Trip trip, boolean last, TransferType transferType) {
        List<StopTime> stopTimes = trip.getStopTimes();
        if (stopTimes.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format("Transfer of type %s leaves out the stop of trip %s, which has no stop times",
                            transferType.name(), trip.getId()));
        }
        StopTime terminal = stopTimes.getFirst();
        for (StopTime stopTime : stopTimes) {
            int comparison = stopTime.compareTo(terminal);
            if (last ? comparison >= 0 : comparison < 0) {
                terminal = stopTime;
            }
        }
        return terminal.stop();
    }

    /**
     * Checks that a transfer names a stop or a station, and a stop if it links trips: GTFS defines transfers between
     * stops and stations only, and trips are linked at the stops of a station.
     */
    private static void checkLocationType(Stop stop, TransferType transferType) {
        LocationType locationType = stop.getLocationType();
        if (locationType == LocationType.STATION && linksTrips(transferType)) {
            throw new IllegalArgumentException(
                    String.format("Transfer of type %s links trips at station %s, not at its stops",
                            transferType.name(), stop.getId()));
        }
        if (locationType != LocationType.STOP && locationType != LocationType.STATION) {
            throw new IllegalArgumentException(
                    String.format("Transfer names stop %s of location type %s, which is neither a stop nor a station",
                            stop.getId(), locationType.name()));
        }
    }

    public GtfsScheduleBuilder addAgency(String id, String name, String url, ZoneId timezone) {
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
        return addStop(id, name, lat, lon, parentStopId, wheelchairBoarding, LocationType.STOP);
    }

    public GtfsScheduleBuilder addStop(String id, String name, double lat, double lon, String parentStopId,
                                       AccessibilityInformation wheelchairBoarding, LocationType locationType) {
        checkNotBuilt();
        if (stops.containsKey(id)) {
            throw new IllegalArgumentException("Stop " + id + " already exists");
        }
        log.debug("Adding stop {}", id);
        Stop stop = new Stop(id, name, new GeoCoordinate(lat, lon), wheelchairBoarding, locationType);

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
            EnumSet<DayOfWeek> dayOfWeek = EnumSet.noneOf(DayOfWeek.class);
            calendar = new Calendar(calendarId, dayOfWeek, null, null);
            calendars.put(calendarId, calendar);
        }

        // check that no exception for defined date is present, each service id / date combination must be unique
        if (calendar.getCalendarDates().containsKey(date)) {
            throw new IllegalArgumentException(
                    "Date " + date + " is already defined as an exception for calendar " + calendarId);
        }

        log.debug("Adding calendar {}-{}", calendarId, date);
        CalendarDate calendarDate = new CalendarDate(calendar, localDateCache.getOrAdd(date), type);
        calendar.addCalendarDate(calendarDate);
        return this;
    }

    public GtfsScheduleBuilder addTrip(String id, String routeId, String serviceId, String headSign) {
        return addTrip(id, routeId, serviceId, headSign, AccessibilityInformation.UNKNOWN, BikeInformation.UNKNOWN);
    }

    public GtfsScheduleBuilder addTrip(String id, String routeId, String serviceId, String headSign,
                                       AccessibilityInformation wheelchairAccessible, BikeInformation bikesAllowed) {
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
        return addStopTime(tripId, stopId, arrival, departure, PickupDropOffType.REGULAR, PickupDropOffType.REGULAR);
    }

    public GtfsScheduleBuilder addStopTime(String tripId, String stopId, ServiceDayTime arrival,
                                           ServiceDayTime departure, PickupDropOffType pickupType,
                                           PickupDropOffType dropOffType) {
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
                serviceDayTimeCache.getOrAdd(departure), pickupType, dropOffType);
        stop.addStopTime(stopTime);
        trip.addStopTime(stopTime);
        return this;
    }

    public GtfsScheduleBuilder addTransfer(String fromStopId, String toStopId, TransferType transferType,
                                           @Nullable Integer minTransferTime) {
        return addTransfer(fromStopId, toStopId, null, null, null, null, transferType, minTransferTime);
    }

    /**
     * Adds a transfer, which may apply to particular routes or trips only, see
     * {@link org.naviqore.timetable.model.Transfer}. A transfer of the in-seat types links two trips, which it has to
     * name, and may leave out either stop: it is then from the last stop of the arriving trip, or to the first stop of
     * the departing one.
     * <p>
     * A transfer that names trips is checked against the stop times added so far, so it is added after them: each trip
     * it names has to call at the stop of its side, or at a stop of the station.
     *
     * @throws IllegalArgumentException if the transfer cannot be applied: a stop, route or trip it names does not
     *                                  exist, a trip is not of the route named with it or does not call at its stop, a
     *                                  stop is neither a stop nor a station, or a station where it links trips, the
     *                                  minimum transfer time is negative or missing for its type, or it does not name
     *                                  what its type requires.
     */
    public GtfsScheduleBuilder addTransfer(@Nullable String fromStopId, @Nullable String toStopId,
                                           @Nullable String fromRouteId, @Nullable String toRouteId,
                                           @Nullable String fromTripId, @Nullable String toTripId,
                                           TransferType transferType, @Nullable Integer minTransferTime) {
        checkNotBuilt();
        Route fromRoute = fromRouteId == null ? null : getRoute(fromRouteId);
        Route toRoute = toRouteId == null ? null : getRoute(toRouteId);
        Trip fromTrip = fromTripId == null ? null : getTrip(fromTripId, fromRoute);
        Trip toTrip = toTripId == null ? null : getTrip(toTripId, toRoute);
        if (minTransferTime != null && minTransferTime < 0) {
            throw new IllegalArgumentException("Minimal transfer time " + minTransferTime + " is negative");
        }
        if (transferType == TransferType.MINIMUM_TIME && minTransferTime == null) {
            throw new IllegalArgumentException(
                    "Minimal transfer time is not present for transfer of type " + transferType.name() + " from stop " + fromStopId + " to stop " + toStopId);
        }
        boolean linksTrips = linksTrips(transferType);
        if (linksTrips && (fromTrip == null || toTrip == null)) {
            throw new IllegalArgumentException(
                    "Transfer of type " + transferType.name() + " from stop " + fromStopId + " to stop " + toStopId + " does not name both trips");
        }
        if (!linksTrips && (fromStopId == null || toStopId == null)) {
            throw new IllegalArgumentException("Transfer of type " + transferType.name() + " does not name both stops");
        }

        // a transfer linking trips that leaves out a stop has both trips
        Stop fromStop = fromStopId == null ? getTerminalStop(Objects.requireNonNull(fromTrip), true,
                transferType) : getStop(fromStopId);
        Stop toStop = toStopId == null ? getTerminalStop(Objects.requireNonNull(toTrip), false,
                transferType) : getStop(toStopId);
        checkLocationType(fromStop, transferType);
        checkLocationType(toStop, transferType);
        if (fromTrip != null) {
            checkCallsAt(fromTrip, fromStop);
        }
        if (toTrip != null) {
            checkCallsAt(toTrip, toStop);
        }

        log.debug("Adding transfer {}-{} of type {} {}", fromStop.getId(), toStop.getId(), transferType,
                minTransferTime);
        fromStop.addTransfer(
                new Transfer(fromStop, toStop, fromRoute, toRoute, fromTrip, toTrip, transferType, minTransferTime));
        return this;
    }

    private void checkCallsAt(Trip trip, Stop stop) {
        List<Stop> children = parents.getOrDefault(stop.getId(), List.of());
        for (StopTime stopTime : trip.getStopTimes()) {
            if (stopTime.stop().equals(stop) || children.contains(stopTime.stop())) {
                return;
            }
        }
        throw new IllegalArgumentException("Trip " + trip.getId() + " does not call at stop " + stop.getId());
    }

    private Stop getStop(String stopId) {
        Stop stop = stops.get(stopId);
        if (stop == null) {
            throw new IllegalArgumentException("Stop " + stopId + " does not exist");
        }
        return stop;
    }

    private Route getRoute(String routeId) {
        Route route = routes.get(routeId);
        if (route == null) {
            throw new IllegalArgumentException("Route " + routeId + " does not exist");
        }
        return route;
    }

    private Trip getTrip(String tripId, @Nullable Route route) {
        Trip trip = trips.get(tripId);
        if (trip == null) {
            throw new IllegalArgumentException("Trip " + tripId + " does not exist");
        }
        if (route != null && !trip.getRoute().equals(route)) {
            throw new IllegalArgumentException("Trip " + tripId + " does not belong to route " + route.getId());
        }
        return trip;
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

        if (calendars.isEmpty()) {
            throw new IllegalArgumentException("At least one calendar has to be set.");
        }

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
