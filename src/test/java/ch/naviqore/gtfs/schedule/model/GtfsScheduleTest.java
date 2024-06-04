package ch.naviqore.gtfs.schedule.model;

import ch.naviqore.gtfs.schedule.type.ExceptionType;
import ch.naviqore.gtfs.schedule.type.ServiceDayTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(GtfsScheduleTestExtension.class)
class GtfsScheduleTest {

    private GtfsScheduleTestBuilder testBuilder;
    private GtfsScheduleBuilder builder;

    @BeforeEach
    void setUp(GtfsScheduleTestBuilder testBuilder) {
        this.testBuilder = testBuilder;
        builder = testBuilder.withAddAgency()
                .withAddCalendars()
                .withAddCalendarDates()
                .withAddInterCity()
                .withAddUnderground()
                .withAddBus()
                .withAddTransfers()
                .getBuilder();
    }

    @Test
    void shouldPreventMultipleBuildCalls() {
        builder.build();
        assertThatThrownBy(() -> builder.build()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldAllowBuildAfterReset() {
        builder.build();
        // reset the test builder which internally also reset the gtfs builder, the instance / reference stays the same.
        testBuilder.reset();
        // add again some GTFS test data
        testBuilder.withAddAgency()
                .withAddCalendars()
                .withAddCalendarDates()
                .withAddInterCity()
                .withAddUnderground()
                .withAddBus()
                .withAddTransfers();
        // use same builder again
        GtfsSchedule schedule = builder.build();
        assertThat(schedule.getAgencies()).isNotEmpty();
        assertThat(schedule.getCalendars()).isNotEmpty();
        assertThat(schedule.getStops()).isNotEmpty();
        assertThat(schedule.getRoutes()).isNotEmpty();
        assertThat(schedule.getTrips()).isNotEmpty();
    }

    @Nested
    class Builder {

        @Nested
        class Schedule {

            private GtfsSchedule schedule;

            @BeforeEach
            void setUp() {
                schedule = builder.build();
            }

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

            @Nested
            class Immutability {

                @Test
                void shouldPreventModificationsToRoutes() {
                    assertThatThrownBy(() -> schedule.getRoutes().put("", null)).isInstanceOf(
                            UnsupportedOperationException.class);
                }

                @Test
                void shouldPreventModificationsToStops() {
                    assertThatThrownBy(() -> schedule.getStops().put("", null)).isInstanceOf(
                            UnsupportedOperationException.class);
                }

                @Test
                void shouldPreventModificationsToTrips() {
                    assertThatThrownBy(() -> schedule.getTrips().put("", null)).isInstanceOf(
                            UnsupportedOperationException.class);
                }

                @Test
                void shouldPreventModificationsToAgencies() {
                    assertThatThrownBy(() -> schedule.getAgencies().put("", null)).isInstanceOf(
                            UnsupportedOperationException.class);
                }

                @Test
                void shouldPreventModificationsToCalendars() {
                    assertThatThrownBy(() -> schedule.getCalendars().put("", null)).isInstanceOf(
                            UnsupportedOperationException.class);
                }

                @Test
                void shouldPreventModificationOfTripsOnRoute() {
                    assertThatThrownBy(
                            () -> schedule.getRoutes().values().iterator().next().addTrip(null)).isInstanceOf(
                            UnsupportedOperationException.class);
                }

                @Test
                void shouldPreventModificationOfTransfersOnStop() {
                    assertThatThrownBy(
                            () -> schedule.getStops().values().iterator().next().addTransfer(null)).isInstanceOf(
                            UnsupportedOperationException.class);
                }

                @Test
                void shouldPreventModificationOfStopTimesOnStop() {
                    assertThatThrownBy(
                            () -> schedule.getStops().values().iterator().next().addStopTime(null)).isInstanceOf(
                            UnsupportedOperationException.class);
                }

                @Test
                void shouldPreventModificationOfStopTimesOnTrip() {
                    assertThatThrownBy(
                            () -> schedule.getTrips().values().iterator().next().addStopTime(null)).isInstanceOf(
                            UnsupportedOperationException.class);
                }

                @Test
                void shouldPreventModificationOfCalendarDatesOnCalendar() {
                    Calendar calendar = schedule.getCalendars().values().iterator().next();
                    assertThatThrownBy(() -> calendar.addCalendarDate(
                            new CalendarDate(calendar, LocalDate.now(), ExceptionType.ADDED))).isInstanceOf(
                            UnsupportedOperationException.class);
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
                    assertThat(departures).hasSize(LIMIT)
                            .extracting(StopTime::departure)
                            .containsExactlyElementsOf(expectedDepartures);

                    // assert trips are correct
                    List<String> expectedTripIds = List.of("route1_we_f_4", "route1_we_r_4", "route1_we_f_5",
                            "route1_we_r_5", "route1_we_f_6");
                    List<String> tripIds = departures.stream().map(stopTime -> stopTime.trip().getId()).toList();
                    assertThat(tripIds).containsExactlyElementsOf(expectedTripIds);

                    // assert routes are correct
                    Set<String> expectedRouteIds = Set.of("route1");
                    List<String> routeIds = departures.stream()
                            .map(stopTime -> stopTime.trip().getRoute().getId())
                            .toList();
                    assertThat(routeIds).allMatch(expectedRouteIds::contains);
                }

                @Test
                void shouldReturnNextDeparturesOnWeekday() {
                    List<StopTime> departures = schedule.getNextDepartures(STOP_ID,
                            GtfsScheduleTestBuilder.Moments.WEEKDAY_8_AM, LIMIT);

                    // assert departures times are correct
                    List<ServiceDayTime> expectedDepartures = List.of(ServiceDayTime.parse("08:00:00"),
                            ServiceDayTime.parse("08:03:00"), ServiceDayTime.parse("08:09:00"),
                            ServiceDayTime.parse("08:09:00"), ServiceDayTime.parse("08:15:00"));
                    assertThat(departures).hasSize(LIMIT)
                            .extracting(StopTime::departure)
                            .containsExactlyElementsOf(expectedDepartures);

                    // assert trips are correct
                    List<String> expectedTripIds = List.of("route3_wd_f_16", "route3_wd_r_17", "route3_wd_f_17",
                            "route3_wd_r_17", "route1_wd_f_7");
                    List<String> tripIds = departures.stream().map(stopTime -> stopTime.trip().getId()).toList();
                    assertThat(tripIds).containsExactlyElementsOf(expectedTripIds);

                    // assert routes are correct
                    Set<String> expectedRouteIds = Set.of("route1", "route3");
                    List<String> routeIds = departures.stream()
                            .map(stopTime -> stopTime.trip().getRoute().getId())
                            .toList();
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
                            ServiceDayTime.parse("24:03:00"), ServiceDayTime.parse("24:09:00"),
                            ServiceDayTime.parse("24:09:00"), ServiceDayTime.parse("24:15:00"));
                    assertThat(departures).hasSize(LIMIT)
                            .extracting(StopTime::departure)
                            .containsExactlyElementsOf(expectedDepartures);

                    // assert trips are correct
                    List<String> expectedTripIds = List.of("route3_wd_f_80", "route3_wd_r_81", "route3_wd_f_81",
                            "route3_wd_r_81", "route1_wd_f_39");
                    List<String> tripIds = departures.stream().map(stopTime -> stopTime.trip().getId()).toList();
                    assertThat(tripIds).containsExactlyElementsOf(expectedTripIds);

                    // assert routes are correct
                    Set<String> expectedRouteIds = Set.of("route1", "route3");
                    List<String> routeIds = departures.stream()
                            .map(stopTime -> stopTime.trip().getRoute().getId())
                            .toList();
                    assertThat(routeIds).allMatch(expectedRouteIds::contains);
                }

                @Test
                void shouldReturnNoNextDeparturesOnNoServiceDay() {
                    assertThat(
                            schedule.getNextDepartures(STOP_ID, GtfsScheduleTestBuilder.Moments.NO_SERVICE.atTime(8, 0),
                                    Integer.MAX_VALUE)).isEmpty();
                }

                @Test
                void shouldReturnNoDeparturesFromUnknownStop() {
                    assertThatThrownBy(
                            () -> schedule.getNextDepartures("unknown", LocalDateTime.now(), 1)).isInstanceOf(
                            IllegalArgumentException.class).hasMessage("Stop unknown not found");
                }
            }

            @Nested
            class ActiveTrips {

                private static void assertWeekendAndHoliday(List<Trip> activeTrips) {
                    assertThat(activeTrips).hasSize(168)
                            .extracting(trip -> trip.getRoute().getId())
                            .containsAll(Set.of("route1", "route2"));
                }

                @Test
                void shouldReturnActiveTripsOnWeekday() {
                    List<Trip> activeTrips = schedule.getActiveTrips(
                            GtfsScheduleTestBuilder.Moments.WEEKDAY_8_AM.toLocalDate());

                    assertThat(activeTrips).hasSize(503)
                            .extracting(trip -> trip.getRoute().getId())
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
                    assertThat(schedule.getActiveTrips(
                            GtfsScheduleTestBuilder.Validity.PERIOD_START.minusDays(1))).isEmpty();
                    assertThat(
                            schedule.getActiveTrips(GtfsScheduleTestBuilder.Validity.PERIOD_END.plusDays(1))).isEmpty();
                }

                @Test
                void shouldReturnNoActiveTripsForNonServiceDay() {
                    assertThat(schedule.getActiveTrips(GtfsScheduleTestBuilder.Moments.NO_SERVICE)).isEmpty();
                }
            }
        }
    }
}