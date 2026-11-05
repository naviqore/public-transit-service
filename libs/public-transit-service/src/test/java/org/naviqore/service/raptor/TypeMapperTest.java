package org.naviqore.service.raptor;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.naviqore.gtfs.schedule.model.GtfsSchedule;
import org.naviqore.gtfs.schedule.model.GtfsScheduleBuilder;
import org.naviqore.gtfs.schedule.type.RouteType;
import org.naviqore.service.*;
import org.naviqore.service.config.ConnectionQueryConfig;
import org.naviqore.service.config.ServiceConfig;
import org.naviqore.service.exception.ConnectionRoutingException;
import org.naviqore.service.exception.StopNotFoundException;
import org.naviqore.timetable.type.ServiceDayTime;

import java.io.IOException;
import java.time.*;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Maps the rides of the router to the calls of their trips in the nights the clocks change. The time of a call counts
 * from "noon minus 12h" of its service day, which on the day of a change is not midnight, and the clocks change while
 * the trips of the day before still run: the time of a call is then not the time on the clock.
 */
class TypeMapperTest {

    private static final ZoneId ZONE_ID = ZoneId.of("Europe/Zurich");
    // the clocks go forward from 02:00 to 03:00 on 29 March 2026, and back from 03:00 to 02:00 on 25 October 2026
    private static final LocalDate SPRING_CHANGE = LocalDate.of(2026, Month.MARCH, 29);
    private static final LocalDate AUTUMN_CHANGE = LocalDate.of(2026, Month.OCTOBER, 25);

    private GtfsSchedule schedule;
    private PublicTransitService service;

    /**
     * Adds a trip from A to B that runs on the given service day only, on a route of its own: the router looks for the
     * trip of a route day by day and takes the first it can reach, and in these nights a trip of the day before can run
     * after one of the day itself.
     */
    private static void addTrip(GtfsScheduleBuilder builder, String id, LocalDate serviceDay, String departure,
                                String arrival) {
        builder.addCalendar(id, EnumSet.allOf(DayOfWeek.class), serviceDay, serviceDay);
        builder.addRoute(id, "agency", id, id, RouteType.parse(3));
        builder.addTrip(id, id, id, "B");
        builder.addStopTime(id, "A", ServiceDayTime.parse(departure), ServiceDayTime.parse(departure));
        builder.addStopTime(id, "B", ServiceDayTime.parse(arrival), ServiceDayTime.parse(arrival));
    }

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        GtfsScheduleBuilder builder = GtfsSchedule.builder();
        builder.addAgency("agency", "Agency", "", ZONE_ID);
        // too far apart to walk
        builder.addStop("A", "A", 47.0, 8.0);
        builder.addStop("B", "B", 47.1, 8.0);
        addTrip(builder, "spring-change-day", SPRING_CHANGE, "01:35:00", "01:50:00");
        addTrip(builder, "spring-day-before", SPRING_CHANGE.minusDays(1), "26:53:00", "27:10:00");
        addTrip(builder, "autumn-change-day", AUTUMN_CHANGE, "00:30:00", "00:45:00");
        addTrip(builder, "autumn-day-before", AUTUMN_CHANGE.minusDays(1), "26:45:00", "27:01:00");
        schedule = builder.build();

        ServiceConfig config = ServiceConfig.builder().timetableRepository(() -> schedule).build();
        service = new PublicTransitServiceFactory(config).create();
    }

    @ParameterizedTest
    @EnumSource(TimeType.class)
    void shouldMapRideOfChangeDayBeforeClocksGoForward(
            TimeType timeType) throws StopNotFoundException, ConnectionRoutingException {
        // the service day starts at 23:00 of the day before, so the trip leaves at 00:35 rather than at 01:35
        assertRide(timeType, "spring-change-day", "2026-03-29T00:35+01:00", "2026-03-29T00:50+01:00");
    }

    @ParameterizedTest
    @EnumSource(TimeType.class)
    void shouldMapRideOfDayBeforeAfterClocksGoForward(
            TimeType timeType) throws StopNotFoundException, ConnectionRoutingException {
        // the clocks have gone forward when the trip leaves, so it leaves at 03:53 rather than at 02:53
        assertRide(timeType, "spring-day-before", "2026-03-29T03:53+02:00", "2026-03-29T04:10+02:00");
    }

    @ParameterizedTest
    @EnumSource(TimeType.class)
    void shouldMapRideOfChangeDayBeforeClocksGoBack(
            TimeType timeType) throws StopNotFoundException, ConnectionRoutingException {
        // the service day starts at 01:00, so the trip leaves at 01:30 rather than at 00:30
        assertRide(timeType, "autumn-change-day", "2026-10-25T01:30+02:00", "2026-10-25T01:45+02:00");
    }

    @ParameterizedTest
    @EnumSource(TimeType.class)
    void shouldMapRideOfDayBeforeAcrossClocksGoingBack(
            TimeType timeType) throws StopNotFoundException, ConnectionRoutingException {
        // the clocks go back while the trip runs: it leaves at 02:45 and arrives sixteen minutes later, at 02:01
        assertRide(timeType, "autumn-day-before", "2026-10-25T02:45+02:00", "2026-10-25T02:01+01:00");
    }

    @Test
    void shouldRejectRideThatItsTripDoesNotRun() {
        // the trip leaves A at 02:45, as the ride does, but arrives at B at 02:01 rather than at 02:30
        Ride ride = new Ride("autumn-day-before", "A", "B", OffsetDateTime.parse("2026-10-25T02:45+02:00"),
                OffsetDateTime.parse("2026-10-25T02:30+01:00"));

        assertThatThrownBy(() -> TypeMapper.map(ride, schedule)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("autumn-day-before");
    }

    private void assertRide(TimeType timeType, String tripId, String departure,
                            String arrival) throws StopNotFoundException, ConnectionRoutingException {
        OffsetDateTime departureTime = OffsetDateTime.parse(departure);
        OffsetDateTime arrivalTime = OffsetDateTime.parse(arrival);

        List<Connection> connections = service.getConnections(service.getStopById("A"), service.getStopById("B"),
                timeType == TimeType.DEPARTURE ? departureTime : arrivalTime, timeType,
                ConnectionQueryConfig.builder().build());

        assertThat(connections).singleElement()
                .extracting(connection -> connection.getLegs().getFirst())
                .isInstanceOfSatisfying(PublicTransitLeg.class, leg -> {
                    assertThat(leg.getTrip().getId()).isEqualTo(tripId);
                    assertThat(leg.getDeparture().getStop().getId()).isEqualTo("A");
                    assertThat(leg.getDeparture().getDepartureTime()).isEqualTo(departureTime);
                    assertThat(leg.getArrival().getStop().getId()).isEqualTo("B");
                    assertThat(leg.getArrival().getArrivalTime()).isEqualTo(arrivalTime);
                });
    }

    /**
     * A ride on a trip, as the router reports it.
     */
    @Getter
    @RequiredArgsConstructor
    private static final class Ride implements org.naviqore.raptor.Leg {

        private final String tripId;
        private final String fromStopId;
        private final String toStopId;
        private final OffsetDateTime departureTime;
        private final OffsetDateTime arrivalTime;
        private final Type type = Type.ROUTE;

        @Override
        public String getRouteId() {
            // each trip runs on a route of the same id
            return tripId;
        }

        @Override
        public int compareTo(org.naviqore.raptor.Leg other) {
            return departureTime.compareTo(other.getDepartureTime());
        }
    }

}
