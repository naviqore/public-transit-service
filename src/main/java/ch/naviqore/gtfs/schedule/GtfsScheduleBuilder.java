package ch.naviqore.gtfs.schedule;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Log4j2
public class GtfsScheduleBuilder {

    private final Map<String, Agency> agencies = new HashMap<>();
    private final Map<String, Stop> stops = new HashMap<>();
    private final Map<String, Route> routes = new HashMap<>();
    private final Map<String, Calendar> calendars = new HashMap<>();
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

    public GtfsScheduleBuilder addCalendar(String id, EnumSet<DayOfWeek> serviceDays, LocalDate startDate,
                                           LocalDate endDate) {
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
        // TODO: Handle calendar dates
        var calendarDate = new CalendarDate(calendar, date, type);
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
        trips.put(id, new Trip(id, route, calendar));
        return this;
    }

    public GtfsSchedule build() {
        return new GtfsSchedule(agencies);
    }
}
