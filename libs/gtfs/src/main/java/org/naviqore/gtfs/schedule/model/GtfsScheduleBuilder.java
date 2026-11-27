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
    // the transfers between linked trips that leave out their stops, added once the stop times of the trips are sorted
    private final List<LinkedTripsTransfer> linkedTripsTransfers = new ArrayList<>();

    private boolean built = false;

    private static boolean linksTrips(TransferType transferType) {
        return transferType == TransferType.IN_SEAT || transferType == TransferType.IN_SEAT_FORBIDDEN;
    }

    /**
     * Checks the transfers from a stop, once the stations have their stops: a trip a transfer names has to call at the
     * stop of its side, or at a stop of the station, and a transfer of the in-seat types has to name stops.
     */
    private static void checkTransfers(Stop stop) {
        for (Transfer transfer : stop.getTransfers()) {
            if (linksTrips(transfer.getTransferType()) && (isStation(transfer.getFromStop()) || isStation(
                    transfer.getToStop()))) {
                throw new IllegalArgumentException(transfer + " links trips at a station, not at its stops");
            }
            transfer.getFromTrip().ifPresent(trip -> checkCallsAt(trip, transfer.getFromStop(), transfer));
            transfer.getToTrip().ifPresent(trip -> checkCallsAt(trip, transfer.getToStop(), transfer));
        }
    }

    // a stop with children is not always a station: a platform may have boarding areas
    private static boolean isStation(Stop stop) {
        return stop.getLocationType() == LocationType.STATION;
    }

    private static void checkCallsAt(Trip trip, Stop stop, Transfer transfer) {
        for (StopTime stopTime : trip.getStopTimes()) {
            if (stopTime.stop().equals(stop) || stopTime.stop().getParent().filter(stop::equals).isPresent()) {
                return;
            }
        }
        throw new IllegalArgumentException(
                transfer + " names trip " + trip.getId() + ", which does not call at stop " + stop.getId());
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
     * name, and may leave out its stops: it is then from the last stop of the arriving trip to the first stop of the
     * departing one.
     * <p>
     * Once the stations have their stops, the schedule is built only if each trip a transfer names calls at the stop of
     * its side, or at a stop of the station, and if no transfer of the in-seat types names a station, a stop of
     * {@link LocationType#STATION}.
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
        if (transferType == TransferType.MINIMUM_TIME && minTransferTime == null) {
            throw new IllegalArgumentException(
                    "Minimal transfer time is not present for transfer of type " + transferType.name() + " from stop " + fromStopId + " to stop " + toStopId);
        }
        boolean linksTrips = linksTrips(transferType);
        if (linksTrips && (fromTrip == null || toTrip == null)) {
            throw new IllegalArgumentException(
                    "Transfer of type " + transferType.name() + " from stop " + fromStopId + " to stop " + toStopId + " does not name both trips");
        }

        if (fromStopId == null || toStopId == null) {
            if (!linksTrips) {
                throw new IllegalArgumentException(
                        "Transfer of type " + transferType.name() + " does not name both stops");
            }
            linkedTripsTransfers.add(new LinkedTripsTransfer(fromRoute, toRoute, Objects.requireNonNull(fromTrip),
                    Objects.requireNonNull(toTrip), transferType, minTransferTime));
            return this;
        }

        Stop fromStop = getStop(fromStopId);
        Stop toStop = getStop(toStopId);
        log.debug("Adding transfer {}-{} of type {} {}", fromStopId, toStopId, transferType, minTransferTime);
        fromStop.addTransfer(
                new Transfer(fromStop, toStop, fromRoute, toRoute, fromTrip, toTrip, transferType, minTransferTime));
        return this;
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
     * Adds the transfers between linked trips that leave out their stops, from the last stop of the arriving trip to
     * the first stop of the departing one, once the stop times of the trips are sorted.
     */
    private void addLinkedTripsTransfers() {
        for (LinkedTripsTransfer transfer : linkedTripsTransfers) {
            for (Trip trip : List.of(transfer.fromTrip(), transfer.toTrip())) {
                if (trip.getStopTimes().isEmpty()) {
                    throw new IllegalArgumentException(
                            "Transfer from trip " + transfer.fromTrip().getId() + " to trip " + transfer.toTrip()
                                    .getId() + " leaves out its stops, but trip " + trip.getId() + " has no stop " + "times");
                }
            }
            Stop fromStop = transfer.fromTrip().getStopTimes().getLast().stop();
            Stop toStop = transfer.toTrip().getStopTimes().getFirst().stop();
            fromStop.addTransfer(
                    new Transfer(fromStop, toStop, transfer.fromRoute(), transfer.toRoute(), transfer.fromTrip(),
                            transfer.toTrip(), transfer.transferType(), transfer.minTransferTime()));
        }
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

        // initialize: make immutable and resize arrays to capacity, the stops once the transfers between linked trips,
        // which take their stops from the sorted stop times of the trips, are added, and the transfers checked
        trips.values().parallelStream().forEach(Initializable::initialize);
        addLinkedTripsTransfers();
        stops.values().parallelStream().forEach(GtfsScheduleBuilder::checkTransfers);
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
        linkedTripsTransfers.clear();
    }

    private void checkNotBuilt() {
        if (built) {
            throw new IllegalStateException("Cannot modify builder after build() has been called.");
        }
    }

    /**
     * A transfer between linked trips that leaves out its stops.
     */
    private record LinkedTripsTransfer(@Nullable Route fromRoute, @Nullable Route toRoute, Trip fromTrip, Trip toTrip,
                                       TransferType transferType, @Nullable Integer minTransferTime) {
    }

}
