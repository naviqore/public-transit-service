package ch.naviqore.gtfs.schedule.model;

import ch.naviqore.gtfs.schedule.type.ServiceDayTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(GtfsScheduleTestExtension.class)
class GtfsScheduleTest {

    private GtfsSchedule schedule;

    @BeforeEach
    void setUp(GtfsScheduleTestBuilder builder) {
        schedule = builder.withAddAgency().withAddCalendars().withAddCalendarDates().withAddInterCity()
                .withAddUnderground().withAddBus().build();
    }

    @Nested
    class Builder {

        @Test
        void shouldCorrectlyCountAgencies() {
            assertThat(schedule.getAgencies()).hasSize(2);
        }

        @Test
        void shouldCorrectlyCountRoutes() {
            assertThat(schedule.getRoutes()).hasSize(3);
        }

        @Test
        void shouldCorrectlyCountStops() {
            assertThat(schedule.getStops()).hasSize(9);
        }

        @Test
        void shouldCorrectlyCountTrips() {
            assertThat(schedule.getTrips()).hasSize(671);
        }

        @Test
        void shouldCorrectlyCountCalendars() {
            assertThat(schedule.getCalendars()).hasSize(2);
        }
    }

    @Nested
    class NearestStops {

        @Test
        void shouldFindStopWithin1Meter() {
            assertThat(schedule.getNearestStops(47.5, 8.5, 1)).hasSize(1).extracting("id").containsOnly("s2");
        }

        @Test
        void shouldFindStopsWithin10000Meters() {
            assertThat(schedule.getNearestStops(47.5, 8.5, 10000)).hasSize(3).extracting("id")
                    .containsOnly("u6", "s2", "u3");
        }

        @Test
        void shouldFindAllStops() {
            assertThat(schedule.getNearestStops(47.5, 8.5, Integer.MAX_VALUE)).hasSize(9).extracting("id")
                    .containsOnly("s1", "s2", "s3", "u1", "u2", "u3", "u4", "u5", "u6");
        }

        @Test
        void shouldFindNoStopsWhenNoneAreCloseEnough() {
            assertThat(schedule.getNearestStops(47.6, 8.5, 100)).isEmpty();
        }
    }

    @Nested
    class NextDepartures {

        private static final String STOP_ID = "s2";
        private static final int LIMIT = 5;

        private static void assertWeekendAndHoliday(List<StopTime> departures) {
            // assert departures times are correct
            List<ServiceDayTime> expectedDepartures = List.of(ServiceDayTime.parse("08:15:00"),
                    ServiceDayTime.parse("08:15:00"), ServiceDayTime.parse("09:15:00"),
                    ServiceDayTime.parse("09:15:00"), ServiceDayTime.parse("10:15:00"));
            assertThat(departures).hasSize(LIMIT).extracting(StopTime::departure)
                    .containsExactlyElementsOf(expectedDepartures);

            // assert trips are correct
            List<String> expectedTripIds = List.of("route1_we_f_4", "route1_we_r_4", "route1_we_f_5", "route1_we_r_5",
                    "route1_we_f_6");
            List<String> tripIds = departures.stream().map(stopTime -> stopTime.trip().getId()).toList();
            assertThat(tripIds).containsExactlyElementsOf(expectedTripIds);

            // assert routes are correct
            Set<String> expectedRouteIds = Set.of("route1");
            List<String> routeIds = departures.stream().map(stopTime -> stopTime.trip().getRoute().getId()).toList();
            assertThat(routeIds).allMatch(expectedRouteIds::contains);
        }

        @Test
        void shouldReturnNextDeparturesOnWeekday() {
            List<StopTime> departures = schedule.getNextDepartures(STOP_ID,
                    GtfsScheduleTestBuilder.Moments.WEEKDAY_8_AM, LIMIT);

            // assert departures times are correct
            List<ServiceDayTime> expectedDepartures = List.of(ServiceDayTime.parse("08:00:00"),
                    ServiceDayTime.parse("08:00:00"), ServiceDayTime.parse("08:09:00"),
                    ServiceDayTime.parse("08:09:00"), ServiceDayTime.parse("08:15:00"));
            assertThat(departures).hasSize(LIMIT).extracting(StopTime::departure)
                    .containsExactlyElementsOf(expectedDepartures);

            // assert trips are correct
            List<String> expectedTripIds = List.of("route3_wd_f_16", "route3_wd_r_16", "route3_wd_f_17",
                    "route3_wd_r_17", "route1_wd_f_7");
            List<String> tripIds = departures.stream().map(stopTime -> stopTime.trip().getId()).toList();
            assertThat(tripIds).containsExactlyElementsOf(expectedTripIds);

            // assert routes are correct
            Set<String> expectedRouteIds = Set.of("route1", "route3");
            List<String> routeIds = departures.stream().map(stopTime -> stopTime.trip().getRoute().getId()).toList();
            assertThat(routeIds).allMatch(expectedRouteIds::contains);
        }

        @Test
        void shouldReturnNextDeparturesOnWeekend() {
            List<StopTime> departures = schedule.getNextDepartures(STOP_ID,
                    GtfsScheduleTestBuilder.Moments.WEEKEND_8_AM, LIMIT);

            assertWeekendAndHoliday(departures);
        }

        @Test
        void shouldReturnNextDeparturesOnHoliday() {
            List<StopTime> departures = schedule.getNextDepartures(STOP_ID,
                    GtfsScheduleTestBuilder.Moments.HOLIDAY.atTime(8, 0), LIMIT);

            assertWeekendAndHoliday(departures);
        }

        @Test
        void shouldReturnNextDeparturesAfterMidnight() {
            List<StopTime> departures = schedule.getNextDepartures(STOP_ID,
                    GtfsScheduleTestBuilder.Moments.WEEKDAY_12_PM, LIMIT);

            // assert departures times are correct
            List<ServiceDayTime> expectedDepartures = List.of(ServiceDayTime.parse("24:00:00"),
                    ServiceDayTime.parse("24:00:00"), ServiceDayTime.parse("24:09:00"),
                    ServiceDayTime.parse("24:09:00"), ServiceDayTime.parse("24:15:00"));
            assertThat(departures).hasSize(LIMIT).extracting(StopTime::departure)
                    .containsExactlyElementsOf(expectedDepartures);

            // assert trips are correct
            List<String> expectedTripIds = List.of("route3_wd_f_80", "route3_wd_r_80", "route3_wd_f_81",
                    "route3_wd_r_81", "route1_wd_f_39");
            List<String> tripIds = departures.stream().map(stopTime -> stopTime.trip().getId()).toList();
            assertThat(tripIds).containsExactlyElementsOf(expectedTripIds);

            // assert routes are correct
            Set<String> expectedRouteIds = Set.of("route1", "route3");
            List<String> routeIds = departures.stream().map(stopTime -> stopTime.trip().getRoute().getId()).toList();
            assertThat(routeIds).allMatch(expectedRouteIds::contains);
        }

        @Test
        void shouldReturnNoNextDeparturesOnNoServiceDay() {
            assertThat(schedule.getNextDepartures(STOP_ID, GtfsScheduleTestBuilder.Moments.NO_SERVICE.atTime(8, 0),
                    Integer.MAX_VALUE)).isEmpty();
        }

        @Test
        void shouldReturnNoDeparturesFromUnknownStop() {
            assertThatThrownBy(() -> schedule.getNextDepartures("unknown", LocalDateTime.now(), 1)).isInstanceOf(
                    IllegalArgumentException.class).hasMessage("Stop unknown not found");
        }
    }

    @Nested
    class ActiveTrips {

        private static void assertWeekendAndHoliday(List<Trip> activeTrips) {
            assertThat(activeTrips).hasSize(168).extracting(trip -> trip.getRoute().getId())
                    .containsAll(Set.of("route1", "route2"));
        }

        @Test
        void shouldReturnActiveTripsOnWeekday() {
            List<Trip> activeTrips = schedule.getActiveTrips(
                    GtfsScheduleTestBuilder.Moments.WEEKDAY_8_AM.toLocalDate());

            assertThat(activeTrips).hasSize(503).extracting(trip -> trip.getRoute().getId())
                    .containsAll(Set.of("route1", "route2", "route3"));
        }

        @Test
        void shouldReturnActiveTripsOnWeekend() {
            List<Trip> activeTrips = schedule.getActiveTrips(
                    GtfsScheduleTestBuilder.Moments.WEEKEND_8_AM.toLocalDate());

            assertWeekendAndHoliday(activeTrips);
        }

        @Test
        void shouldReturnActiveTripsOnHoliday() {
            List<Trip> activeTrips = schedule.getActiveTrips(GtfsScheduleTestBuilder.Moments.HOLIDAY);

            assertWeekendAndHoliday(activeTrips);
        }

        @Test
        void shouldReturnNoActiveTripsForDaysOutsideValidity() {
            assertThat(schedule.getActiveTrips(GtfsScheduleTestBuilder.Validity.PERIOD_START.minusDays(1))).isEmpty();
            assertThat(schedule.getActiveTrips(GtfsScheduleTestBuilder.Validity.PERIOD_END.plusDays(1))).isEmpty();
        }

        @Test
        void shouldReturnNoActiveTripsForNonServiceDay() {
            assertThat(schedule.getActiveTrips(GtfsScheduleTestBuilder.Moments.NO_SERVICE)).isEmpty();
        }
    }
}
