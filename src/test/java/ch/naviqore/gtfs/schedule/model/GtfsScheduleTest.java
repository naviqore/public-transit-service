package ch.naviqore.gtfs.schedule.model;

import ch.naviqore.gtfs.schedule.type.RouteType;
import ch.naviqore.gtfs.schedule.type.ServiceDayTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GtfsScheduleTest {

    private GtfsSchedule schedule;

    @BeforeEach
    void setUp() {
        schedule = GtfsSchedule.builder()
                .addAgency("agency1", "City Transit", "http://citytransit.example.com", "Europe/Zurich")
                .addStop("stop1", "Main Station", 47.3769, 8.5417)
                .addStop("stop2", "Central Park", 47.3779, 8.5407)
                .addStop("stop3", "Hill Valley", 47.3780, 8.5390)
                .addStop("stop4", "East Side", 47.3785, 8.5350)
                .addStop("stop5", "West End", 47.3750, 8.5300)
                .addRoute("route1", "agency1", "101", "Main Line", RouteType.BUS)
                .addRoute("route2", "agency1", "102", "Cross Town", RouteType.BUS)
                .addRoute("route3", "agency1", "103", "Circulator", RouteType.BUS)
                .addCalendar("weekdays", EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), LocalDate.now(),
                        LocalDate.now().plusMonths(1))
                .addCalendar("weekends", EnumSet.of(DayOfWeek.SATURDAY), LocalDate.now(), LocalDate.now().plusMonths(1))
                .addTrip("trip1", "route1", "weekdays")
                .addTrip("trip2", "route2", "weekdays")
                .addTrip("trip3", "route3", "weekends")
                .addStopTime("trip1", "stop1", new ServiceDayTime(8, 0, 0), new ServiceDayTime(8, 5, 0))
                .addStopTime("trip1", "stop2", new ServiceDayTime(8, 10, 0), new ServiceDayTime(8, 15, 0))
                .addStopTime("trip2", "stop3", new ServiceDayTime(9, 0, 0), new ServiceDayTime(9, 5, 0))
                .addStopTime("trip2", "stop4", new ServiceDayTime(9, 10, 0), new ServiceDayTime(9, 15, 0))
                .addStopTime("trip3", "stop5", new ServiceDayTime(10, 0, 0), new ServiceDayTime(10, 5, 0))
                .addStopTime("trip3", "stop1", new ServiceDayTime(10, 10, 0), new ServiceDayTime(10, 15, 0))
                .build();
    }

    @Nested
    class Builder {

        @Test
        void shouldCorrectlyCountAgencies() {
            assertThat(schedule.getAgencies()).hasSize(1);
        }

        @Test
        void shouldCorrectlyCountRoutes() {
            assertThat(schedule.getRoutes()).hasSize(3);
        }

        @Test
        void shouldCorrectlyCountStops() {
            assertThat(schedule.getStops()).hasSize(5);
        }

        @Test
        void shouldCorrectlyCountTrips() {
            assertThat(schedule.getTrips()).hasSize(3);
        }

        @Test
        void shouldCorrectlyCountCalendars() {
            assertThat(schedule.getCalendars()).hasSize(2);
        }
    }

    @Nested
    class NearestStops {

        @Test
        void shouldFindStopsWithin500Meters() {
            assertThat(schedule.getNearestStops(47.3769, 8.5417, 500)).hasSize(3)
                    .extracting("id")
                    .containsOnly("stop1", "stop2", "stop3");
        }

        @Test
        void shouldFindNoStopsWhenNoneAreCloseEnough() {
            assertThat(schedule.getNearestStops(47.3800, 8.5500, 100)).isEmpty();
        }
    }

    @Nested
    class NextDepartures {

        @Test
        void shouldReturnNextDeparturesOnWeekday() {
            // 8:00 AM on a weekday
            LocalDateTime now = LocalDateTime.of(2024, Month.APRIL, 26, 8, 0);
            assertThat(schedule.getNextDepartures("stop1", now, Integer.MAX_VALUE)).hasSize(1);
        }

        @Test
        void shouldReturnNoNextDeparturesOnWeekday() {
            // 9:00 AM on a weekday
            LocalDateTime now = LocalDateTime.of(2024, Month.APRIL, 26, 9, 0);
            assertThat(schedule.getNextDepartures("stop1", now, Integer.MAX_VALUE)).isEmpty();
        }

        @Test
        void shouldReturnNextDeparturesOnSaturday() {
            // 9:00 AM on a Saturday
            LocalDateTime now = LocalDateTime.of(2024, Month.APRIL, 27, 8, 0);
            assertThat(schedule.getNextDepartures("stop1", now, Integer.MAX_VALUE)).hasSize(1);
        }

        @Test
        void shouldReturnNoDeparturesFromUnknownStop() {
            LocalDateTime now = LocalDateTime.of(2024, Month.APRIL, 26, 10, 0);
            assertThatThrownBy(() -> schedule.getNextDepartures("unknown", now, 1)).isInstanceOf(
                    IllegalArgumentException.class).hasMessage("Stop unknown not found");
        }
    }

    @Nested
    class ActiveTrips {

        @Test
        void shouldReturnActiveTripsOnWeekday() {
            assertThat(schedule.getActiveTrips(LocalDate.now())).hasSize(2)
                    .extracting("id")
                    .containsOnly("trip1", "trip2");
        }

        @Test
        void shouldReturnActiveTripsOnWeekend() {
            assertThat(schedule.getActiveTrips(LocalDate.now().with(DayOfWeek.SATURDAY))).hasSize(1)
                    .extracting("id")
                    .containsOnly("trip3");
        }

        @Test
        void shouldReturnNoActiveTripsForNonServiceDay() {
            LocalDate nonServiceDay = LocalDate.now().with(DayOfWeek.SUNDAY).plusWeeks(2);
            assertThat(schedule.getActiveTrips(nonServiceDay)).isEmpty();
        }
    }
}
