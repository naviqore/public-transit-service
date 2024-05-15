package ch.naviqore.gtfs.schedule.model;

import ch.naviqore.gtfs.schedule.type.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.*;

/**
 * Test builder to set up a GTFS schedule for testing purposes.
 * <p>
 * Simple example schedule:
 * <pre>
 *            u1 ------------------ u2
 *            |                   / |
 *            |                /    |
 *            |             /       |
 * s1 ------- u6 ------- s2 ------- u3 ------- s3
 *            |        /            |
 *            |     /               |
 *            |  /                  |
 *            u5 ------------------ u4
 * </pre>
 * Routes:
 * <ul>
 *   <li><b>route1</b> - Everyday service, InterCity train from Other City to Different City via Main Station (s1, s2, s3)</li>
 *   <li><b>route2</b> - Everyday service, Underground system covering six stops (u1, u2, u3, u4, u5, u6)</li>
 *   <li><b>route3</b> - Weekday service, Bus between South-West, Main Station, and North-East (u5, s2, u2)</li>
 * </ul>
 * Stations:
 * <ul>
 *   <li><b>s1</b> - Other City (47.5, 7.5)</li>
 *   <li><b>s2</b> - Main Station (47.5, 8.5)</li>
 *   <li><b>s3</b> - Different City (47.5, 9.5)</li>
 * </ul>
 * Underground:
 * <ul>
 *   <li><b>u1</b> - North-West (47.6, 8.4)</li>
 *   <li><b>u2</b> - North-East (47.6, 8.6)</li>
 *   <li><b>u3</b> - East (47.5, 8.6)</li>
 *   <li><b>u4</b> - South-East (47.4, 8.6)</li>
 *   <li><b>u5</b> - South-West (47.4, 8.4)</li>
 *   <li><b>u6</b> - West (47.5, 8.4)</li>
 * </ul>
 * Transfers:
 * <ul>
 *   <li><b>u6-u5</b> - Minimum time transfer requiring 540 seconds, reflecting a downhill walking path between these stations.</li>
 *   <li><b>u5-u6</b> - Minimum time transfer requiring 600 seconds, reflecting an uphill walking path between these stations.</li>
 *   <li><b>u6</b> - Marking transfer at u6 as not possible, emphasizing route planning around unavailable transfers.</li>
 *   <li><b>u3</b> - Recommendation to transfer at u3 instead of u6 when traveling from East to West.</li>
 * </ul>
 *
 * @author munterfi
 */
@NoArgsConstructor
public class GtfsScheduleTestBuilder {

    private static final int NO_HEADWAY = -1;
    private static final Map<String, Stop> STOPS = Map.of("s1", new Stop("s1", "Other City", 47.5, 7.5), "s2",
            new Stop("s2", "Main Station", 47.5, 8.5), "s3", new Stop("s3", "Different City", 47.5, 9.5), "u1",
            new Stop("u1", "North-West", 47.6, 8.4), "u2", new Stop("u2", "North-East", 47.6, 8.6), "u3",
            new Stop("u3", "East", 47.5, 8.6), "u4", new Stop("u4", "South-East", 47.4, 8.6), "u5",
            new Stop("u5", "South-West", 47.4, 8.4), "u6", new Stop("u6", "West", 47.5, 8.4));
    private static final List<Route> ROUTES = List.of(
            new Route("route1", "agency1", "IC", HierarchicalVehicleType.LONG_DISTANCE_TRAINS, 30, 60, 10, 60, 5,
                    List.of("s1", "s2", "s3")),
            new Route("route2", "agency2", "UNDERGROUND", HierarchicalVehicleType.SUBURBAN_RAILWAY, 5, 10, 10, 3, 1,
                    List.of("u1", "u2", "u3", "u4", "u5", "u6")),
            new Route("route3", "agency2", "BUS", DefaultRouteType.BUS, 15, NO_HEADWAY, 3, 5, 1,
                    List.of("u5", "s2", "s2")));
    private final Set<String> addedStops = new HashSet<>();
    @Getter
    private final GtfsScheduleBuilder builder = GtfsSchedule.builder();

    public GtfsScheduleTestBuilder withAddAgency() {
        builder.addAgency("agency1", "National Transit", "https://nationaltransit.example.com", "Europe/Zurich");
        builder.addAgency("agency2", "City Transit", "https://citytransit.example.com", "Europe/Zurich");
        return this;
    }

    public GtfsScheduleTestBuilder withAddCalendars() {
        builder.addCalendar("weekdays", EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), Validity.PERIOD_START,
                        Validity.PERIOD_END)
                .addCalendar("weekends", EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), Validity.PERIOD_START,
                        Validity.PERIOD_END);
        return this;
    }

    public GtfsScheduleTestBuilder withAddCalendarDates() {
        // change service to sunday
        builder.addCalendarDate("weekdays", Moments.HOLIDAY, ExceptionType.REMOVED);
        builder.addCalendarDate("weekends", Moments.HOLIDAY, ExceptionType.ADDED);
        // no service
        builder.addCalendarDate("weekdays", Moments.NO_SERVICE, ExceptionType.REMOVED);
        return this;
    }

    public GtfsScheduleTestBuilder withAddInterCity() {
        addRoute(ROUTES.getFirst(), true, true);
        return this;
    }

    public GtfsScheduleTestBuilder withAddUnderground() {
        addRoute(ROUTES.get(1), true, false);
        return this;
    }

    public GtfsScheduleTestBuilder withAddBus() {
        addRoute(ROUTES.get(2), false, true);
        return this;
    }

    public GtfsScheduleTestBuilder withAddTransfers() {
        builder.addTransfer("u6", "u5", TransferType.MINIMUM_TIME, 9 * 60);
        builder.addTransfer("u5", "u6", TransferType.MINIMUM_TIME, 10 * 60);
        builder.addTransfer("u6", "u6", TransferType.NOT_POSSIBLE, null);
        builder.addTransfer("u3", "u6", TransferType.NOT_POSSIBLE, null);
        return this;
    }

    public GtfsSchedule build() {
        return builder.build();
    }

    private void addRoute(Route route, boolean everydayService, boolean bidirectional) {
        builder.addRoute(route.id, route.agencyId, route.name, route.name + "long", route.routeType);
        addStops(route);
        addTrips(route, true, false);
        if (everydayService) {
            addTrips(route, false, false);
        }
        if (bidirectional) {
            addTrips(route, true, true);
            if (everydayService) {
                addTrips(route, false, true);
            }
        }
    }

    private void addTrips(Route route, boolean weekday, boolean reverse) {
        final int travelTime = route.travelTime * 60;
        final int dwellTime = route.dwellTime * 60;
        final int headway = weekday ? route.headwayWeekday * 60 : route.headwayWeekend * 60;
        final List<String> routeStops = new ArrayList<>(route.stops);
        String weekdayPostfix = weekday ? "wd" : "we";
        String directionPostfix = "f";
        if (reverse) {
            Collections.reverse(routeStops);
            directionPostfix = "r";
        }
        int tripCount = 0;
        for (int tripDepartureTime = Validity.SERVICE_DAY_START.getTotalSeconds() + route.offset * 60; tripDepartureTime <= Validity.SERVICE_DAY_END.getTotalSeconds(); tripDepartureTime += headway) {
            String tripId = String.format("%s_%s_%s_%s", route.id, weekdayPostfix, directionPostfix, ++tripCount);
            builder.addTrip(tripId, route.id, weekday ? "weekdays" : "weekends");
            int departureTime = tripDepartureTime;
            for (String stopId : routeStops) {
                builder.addStopTime(tripId, stopId, new ServiceDayTime(departureTime - dwellTime),
                        new ServiceDayTime(departureTime));
                departureTime += travelTime + dwellTime;
            }
        }
    }

    private void addStops(Route route) {
        for (String stopId : route.stops) {
            if (!addedStops.contains(stopId)) {
                Stop stop = STOPS.get(stopId);
                builder.addStop(stop.id, stop.id, stop.lat, stop.lon);
                addedStops.add(stopId);
            }
        }
    }

    record Stop(String id, String name, double lat, double lon) {
    }

    record Route(String id, String agencyId, String name, RouteType routeType, int headwayWeekday, int headwayWeekend,
                 int offset, int travelTime, int dwellTime, List<String> stops) {
    }

    public static final class Validity {
        public static final ServiceDayTime SERVICE_DAY_START = new ServiceDayTime(4, 0, 0);
        public static final ServiceDayTime SERVICE_DAY_END = new ServiceDayTime(25, 0, 0);
        public static final LocalDate PERIOD_START = LocalDate.of(2024, Month.JANUARY, 1);
        public static final LocalDate PERIOD_END = LocalDate.of(2024, Month.DECEMBER, 31);
    }

    public static final class Moments {
        public static final LocalDateTime WEEKDAY_8_AM = LocalDateTime.of(2024, Month.APRIL, 26, 8, 0);
        public static final LocalDateTime WEEKDAY_12_PM = LocalDateTime.of(2024, Month.APRIL, 26, 23, 59);
        public static final LocalDateTime WEEKEND_8_AM = LocalDateTime.of(2024, Month.APRIL, 27, 8, 0);
        public static final LocalDate NO_SERVICE = LocalDate.of(2024, Month.MAY, 1);
        public static final LocalDate HOLIDAY = LocalDate.of(2024, Month.DECEMBER, 25);
    }
}
