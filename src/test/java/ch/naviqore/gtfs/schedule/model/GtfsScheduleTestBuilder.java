package ch.naviqore.gtfs.schedule.model;

import ch.naviqore.gtfs.schedule.type.DefaultRouteType;
import ch.naviqore.gtfs.schedule.type.HierarchicalVehicleType;
import ch.naviqore.gtfs.schedule.type.ServiceDayTime;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.EnumSet;

/**
 * Test builder to set up a GTFS schedule for testing purposes.
 *
 * @author munterfi
 */
@NoArgsConstructor
public class GtfsScheduleTestBuilder {

    public static final class Validity {
        public static final LocalDate START_DATE = LocalDate.of(2024, Month.APRIL, 1);
        public static final LocalDate END_DATE = START_DATE.plusMonths(1);
    }

    public static final class Moments {
        public static final LocalDateTime WEEKDAY_8_AM = LocalDateTime.of(2024, Month.APRIL, 26, 8, 0);
        public static final LocalDateTime WEEKDAY_9_AM = WEEKDAY_8_AM.plusHours(1);
        public static final LocalDateTime SATURDAY_9_AM = LocalDateTime.of(2024, Month.APRIL, 27, 9, 0);
    }

    private final GtfsScheduleBuilder builder = GtfsSchedule.builder();

    public GtfsScheduleTestBuilder withAddAgency() {
        builder.addAgency("agency1", "National Transit", "https://nationaltransit.example.com", "Europe/Zurich");
        builder.addAgency("agency2", "City Transit", "https://citytransit.example.com", "Europe/Zurich");
        return this;
    }

    public GtfsScheduleTestBuilder withAddCalendars() {
        builder.addCalendar("weekdays", EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), Validity.START_DATE,
                        Validity.END_DATE)
                .addCalendar("weekends", EnumSet.of(DayOfWeek.SATURDAY), Validity.START_DATE, Validity.END_DATE);
        return this;
    }

    public GtfsScheduleTestBuilder withAddStops() {
        builder.addStop("stop1", "Main Station", 47.3769, 8.5417).addStop("stop2", "Central Park", 47.3779, 8.5407)
                .addStop("stop3", "Hill Valley", 47.3780, 8.5390).addStop("stop4", "East Side", 47.3785, 8.5350)
                .addStop("stop5", "West End", 47.3750, 8.5300);
        return this;
    }

    public GtfsScheduleTestBuilder withAddRoutes() {
        builder.addRoute("route1", "agency1", "101", "Main Line", DefaultRouteType.RAIL)
                .addRoute("route2", "agency2", "102", "Cross Town", DefaultRouteType.BUS)
                .addRoute("route3", "agency2", "103", "Circulator", HierarchicalVehicleType.SUBURBAN_RAILWAY);
        return this;
    }

    public GtfsScheduleTestBuilder withAddTrips() {
        builder.addTrip("trip1", "route1", "weekdays").addTrip("trip2", "route2", "weekdays")
                .addTrip("trip3", "route3", "weekends");
        return this;
    }

    public GtfsScheduleTestBuilder withAddStopTimes() {
        builder.addStopTime("trip1", "stop1", hms("08:00:00"), hms("08:05:00"))
                .addStopTime("trip1", "stop2", hms("08:10:00"), hms("08:15:00"))
                .addStopTime("trip2", "stop3", hms("09:00:00"), hms("09:05:00"))
                .addStopTime("trip2", "stop4", hms("09:10:00"), hms("09:15:00"))
                .addStopTime("trip3", "stop5", hms("10:00:00"), hms("10:05:00"))
                .addStopTime("trip3", "stop1", hms("10:10:00"), hms("10:15:00"));
        return this;
    }

    public GtfsSchedule build() {
        return builder.build();
    }

    private static ServiceDayTime hms(String time) {
        return ServiceDayTime.parse(time);
    }
}
