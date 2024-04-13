package ch.naviqore.gtfs.schedule.model;

import ch.naviqore.gtfs.schedule.type.ExceptionType;
import ch.naviqore.gtfs.schedule.type.RouteType;
import ch.naviqore.gtfs.schedule.type.ServiceDayTime;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * General Transit Feed Specification (GTFS) schedule builder
 * <p>
 * Provides a builder pattern implementation for constructing a {@link GtfsSchedule} instance. This class encapsulates
 * the complexity of assembling a GTFS schedule by incrementally adding components such as agencies, stops, routes,
 * trips, and calendars. The builder ensures that all components are added in a controlled manner and that the resulting
 * schedule is consistent and ready for use.
 *
 * <p>Instances of this class should be obtained through the static {@code builder()} method. This class uses
 * a private constructor to enforce the use of the builder pattern.</p>
 *
 * @author munterfi
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Log4j2
public class GtfsScheduleBuilder {

    private final Map<String, Agency> agencies = new HashMap<>();
    private final Map<String, Calendar> calendars = new HashMap<>();
    private final Map<String, Stop> stops = new HashMap<>();
    private final Map<String, Route> routes = new HashMap<>();
    private final Map<String, Trip> trips = new HashMap<>();

    public static GtfsScheduleBuilder builder() {
        return new GtfsScheduleBuilder();
    }

    public GtfsScheduleBuilder addAgency(String id, String name, String url, String timezone) {
        if (agencies.containsKey(id)) {
            throw new IllegalArgumentException("Agency " + id + " already exists");
        }
        log.debug("Adding agency {}", id);
        agencies.put(id, new Agency(id, name, url, timezone));
        return this;
    }

    public GtfsScheduleBuilder addStop(String id, String name, double lat, double lon) {
        if (stops.containsKey(id)) {
            throw new IllegalArgumentException("Agency " + id + " already exists");
        }
        log.debug("Adding stop {}", id);
        stops.put(id, new Stop(id, name, lat, lon));
        return this;
    }

    public GtfsScheduleBuilder addRoute(String id, String agencyId, String shortName, String longName, RouteType type) {
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

    public GtfsScheduleBuilder addCalendar(String id, EnumSet<DayOfWeek> serviceDays, LocalDate startDate, LocalDate endDate) {
        if (calendars.containsKey(id)) {
            throw new IllegalArgumentException("Calendar " + id + " already exists");
        }
        log.debug("Adding calendar {}", id);
        calendars.put(id, new Calendar(id, serviceDays, startDate, endDate));
        return this;
    }

    public GtfsScheduleBuilder addCalendarDate(String calendarId, LocalDate date, ExceptionType type) {
        Calendar calendar = calendars.get(calendarId);
        if (calendar == null) {
            throw new IllegalArgumentException("Calendar " + calendarId + " does not exist");
        }
        log.debug("Adding calendar {}-{}", calendarId, date);
        CalendarDate calendarDate = new CalendarDate(calendar, date, type);
        calendar.addCalendarDate(calendarDate);
        return this;
    }

    public GtfsScheduleBuilder addTrip(String id, String routeId, String serviceId) {
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
        Trip trip = new Trip(id, route, calendar);
        route.addTrip(trip);
        trips.put(id, trip);
        return this;
    }

    public GtfsScheduleBuilder addStopTime(String tripId, String stopId, ServiceDayTime arrival, ServiceDayTime departure) {
        Trip trip = trips.get(tripId);
        if (trip == null) {
            throw new IllegalArgumentException("Trip " + tripId + " does not exist");
        }
        Stop stop = stops.get(stopId);
        if (stop == null) {
            throw new IllegalArgumentException("Stop " + stopId + " does not exist");
        }
        log.debug("Adding stop {} to trip {} ({}-{})", stopId, tripId, arrival, departure);
        StopTime stopTime = new StopTime(stop, trip, arrival, departure);
        stop.addStopTime(stopTime);
        trip.addStopTime(stopTime);
        return this;
    }

    public GtfsSchedule build() {
        log.info("Building schedule with {} stops, {} routes and {} trips", stops.size(), routes.size(), trips.size());
        trips.values().parallelStream().forEach(Trip::initialize);
        stops.values().parallelStream().forEach(Stop::initialize);
        routes.values().parallelStream().forEach(Route::initialize);
        return new GtfsSchedule(agencies, calendars, stops, routes, trips);
    }
}
