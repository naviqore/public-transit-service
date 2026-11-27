package org.naviqore.service.raptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Isolines with a time window report, per stop, the shortest trip that departs in the window, also when a trip after
 * the window arrives at the same time.
 */
class IsolineTimeWindowTest {

    private static final ZoneId ZONE_ID = ZoneId.of("Europe/Zurich");
    private static final LocalDate DATE = LocalDate.of(2026, Month.JANUARY, 14);

    private PublicTransitService service;

    private static OffsetDateTime at(String time) {
        return DATE.atTime(LocalTime.parse(time)).atZone(ZONE_ID).toOffsetDateTime();
    }

    /**
     * Adds a trip from one stop to another on the given route.
     */
    private static void addTrip(GtfsScheduleBuilder builder, String routeId, String id, String from, String departure,
                                String to, String arrival) {
        builder.addTrip(id, routeId, "daily", to);
        builder.addStopTime(id, from, ServiceDayTime.parse(departure), ServiceDayTime.parse(departure));
        builder.addStopTime(id, to, ServiceDayTime.parse(arrival), ServiceDayTime.parse(arrival));
    }

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        GtfsScheduleBuilder builder = GtfsSchedule.builder();
        builder.addAgency("agency", "Agency", "", ZONE_ID);
        builder.addCalendar("daily", EnumSet.allOf(DayOfWeek.class), DATE.minusDays(7), DATE.plusDays(7));
        // too far apart to walk
        builder.addStop("A", "A", 47.0, 8.0);
        builder.addStop("B", "B", 47.1, 8.0);
        builder.addStop("C", "C", 47.0, 8.1);
        builder.addStop("X", "X", 47.2, 8.0);
        for (String route : new String[]{"direct", "to-c", "to-b", "from-b"}) {
            builder.addRoute(route, "agency", route, route, RouteType.parse(3));
        }
        // from A: directly to X at 08:58, to C at 08:58 and 08:59, and to X via B at 09:05, which arrives at 10:00 too
        addTrip(builder, "direct", "direct", "A", "08:58:00", "X", "10:00:00");
        addTrip(builder, "to-c", "to-c-1", "A", "08:58:00", "C", "09:08:00");
        addTrip(builder, "to-c", "to-c-2", "A", "08:59:00", "C", "09:09:00");
        addTrip(builder, "to-b", "to-b", "A", "09:05:00", "B", "09:10:00");
        addTrip(builder, "from-b", "from-b", "B", "09:30:00", "X", "10:00:00");
        GtfsSchedule schedule = builder.build();

        service = new PublicTransitServiceFactory(
                ServiceConfig.builder().timetableRepository(() -> schedule).build()).create();
    }

    @Test
    void shouldReachStopByTripDepartingInTheWindow() throws StopNotFoundException, ConnectionRoutingException {
        // the window from 08:58 to 08:59:30 holds the departures at 08:58 and 08:59; routed from 08:59, X is reached at
        // 10:00 by the trip at 09:05, after the window, and the trip at 08:58 arrives no earlier
        ConnectionQueryConfig config = ConnectionQueryConfig.builder().timeWindowDuration(90).build();

        Map<Stop, Connection> isolines = service.getIsolines(service.getStopById("A"), at("08:58"), TimeType.DEPARTURE,
                config);

        Connection connection = isolines.get(service.getStopById("X"));
        assertThat(connection).isNotNull();
        assertThat(connection.getDepartureTime()).isEqualTo(at("08:58"));
        assertThat(connection.getArrivalTime()).isEqualTo(at("10:00"));
    }

}
