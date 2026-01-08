package org.naviqore.gtfs.schedule.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.naviqore.gtfs.schedule.type.ExceptionType;
import org.naviqore.gtfs.schedule.type.ServiceDayTime;
import org.naviqore.gtfs.schedule.type.TimeType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
            class GetStopTimes {

                private static final String STOP_ID = "s2";

                @Test
                void shouldFilterByPhysicalTimeWindow() {
                    OffsetDateTime from = GtfsScheduleTestBuilder.Moments.WEEKDAY_8_AM;
                    OffsetDateTime until = from.plusMinutes(10);

                    List<StopTime> results = schedule.getStopTimes(STOP_ID, from, until, TimeType.DEPARTURE);

                    assertThat(results).hasSize(4);
                    assertThat(results.get(0).departure().toString()).isEqualTo("08:00:00");
                    assertThat(results.get(3).departure().toString()).isEqualTo("08:09:00");
                }

                @Test
                void shouldRespectExclusiveUpperBoundary() {
                    OffsetDateTime from = GtfsScheduleTestBuilder.Moments.WEEKDAY_8_AM;
                    OffsetDateTime until = from.plusMinutes(0);

                    assertThat(schedule.getStopTimes(STOP_ID, from, until, TimeType.DEPARTURE)).isEmpty();
                }

                @Test
                void shouldHandleLateNightOverlaps() {
                    // query starts exactly at midnight of the next calendar day
                    OffsetDateTime midnight = GtfsScheduleTestBuilder.Moments.WEEKDAY_12_PM.plusSeconds(1);
                    OffsetDateTime until = midnight.plusMinutes(15);

                    List<StopTime> results = schedule.getStopTimes(STOP_ID, midnight, until, TimeType.DEPARTURE);

                    // verify that trips scheduled as 24:xx:xx from previous service day are caught
                    assertThat(results).isNotEmpty();
                    assertThat(results.getFirst().departure().getTotalSeconds()).isGreaterThanOrEqualTo(24 * 3600);
                }

                @Test
                void shouldSupportArrivalBoards() {
                    OffsetDateTime from = GtfsScheduleTestBuilder.Moments.WEEKDAY_8_AM;
                    OffsetDateTime until = from.plusMinutes(10);

                    List<StopTime> results = schedule.getStopTimes(STOP_ID, from, until, TimeType.ARRIVAL);

                    assertThat(results).isNotEmpty();
                    for (int i = 0; i < results.size() - 1; i++) {
                        Instant current = results.get(i)
                                .arrival()
                                .toZonedDateTime(from.toLocalDate(), GtfsScheduleTestBuilder.ZONE_ID)
                                .toInstant();
                        Instant next = results.get(i + 1)
                                .arrival()
                                .toZonedDateTime(from.toLocalDate(), GtfsScheduleTestBuilder.ZONE_ID)
                                .toInstant();
                        assertThat(current).isBeforeOrEqualTo(next);
                    }
                }

                @Test
                void shouldHandleEmptyResultsOnNoServiceDays() {
                    OffsetDateTime from = GtfsScheduleTestBuilder.Moments.NO_SERVICE_8_AM;
                    OffsetDateTime until = from.plusHours(1);

                    assertThat(schedule.getStopTimes(STOP_ID, from, until, TimeType.DEPARTURE)).isEmpty();
                }

                @Nested
                class ServiceBoardScenarios {

                    private void assertWeekendAndHolidayBehavior(List<StopTime> departures) {
                        List<ServiceDayTime> expectedTimes = List.of(ServiceDayTime.parse("08:15:00"),
                                ServiceDayTime.parse("08:15:00"), ServiceDayTime.parse("09:15:00"),
                                ServiceDayTime.parse("09:15:00"));
                        assertThat(departures).extracting(StopTime::departure).containsExactlyElementsOf(expectedTimes);

                        List<String> expectedTrips = List.of("route1_we_f_4", "route1_we_r_4", "route1_we_f_5",
                                "route1_we_r_5");
                        assertThat(departures).extracting(st -> st.trip().getId())
                                .containsExactlyElementsOf(expectedTrips);
                    }

                    @Test
                    void shouldResolveWeekdayStationBoard() {
                        List<StopTime> results = schedule.getStopTimes(STOP_ID,
                                GtfsScheduleTestBuilder.Moments.WEEKDAY_8_AM,
                                GtfsScheduleTestBuilder.Moments.WEEKDAY_8_AM.plusMinutes(10), TimeType.DEPARTURE);

                        List<ServiceDayTime> expected = List.of(ServiceDayTime.parse("08:00:00"),
                                ServiceDayTime.parse("08:03:00"), ServiceDayTime.parse("08:09:00"),
                                ServiceDayTime.parse("08:09:00"));
                        assertThat(results).extracting(StopTime::departure).containsExactlyElementsOf(expected);
                    }

                    @Test
                    void shouldResolveWeekendStationBoard() {
                        List<StopTime> results = schedule.getStopTimes(STOP_ID,
                                GtfsScheduleTestBuilder.Moments.WEEKEND_8_AM,
                                GtfsScheduleTestBuilder.Moments.WEEKEND_8_AM.plusHours(2), TimeType.DEPARTURE);

                        assertWeekendAndHolidayBehavior(results);
                    }

                    @Test
                    void shouldResolveHolidayStationBoard() {
                        List<StopTime> results = schedule.getStopTimes(STOP_ID,
                                GtfsScheduleTestBuilder.Moments.HOLIDAY_8_AM,
                                GtfsScheduleTestBuilder.Moments.HOLIDAY_8_AM.plusHours(2), TimeType.DEPARTURE);

                        assertWeekendAndHolidayBehavior(results);
                    }
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
                    List<Trip> activeTrips = schedule.getActiveTrips(
                            GtfsScheduleTestBuilder.Moments.HOLIDAY_8_AM.toLocalDate());

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
                    assertThat(schedule.getActiveTrips(
                            GtfsScheduleTestBuilder.Moments.NO_SERVICE_8_AM.toLocalDate())).isEmpty();
                }
            }
        }
    }
}