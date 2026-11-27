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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Connections and isolines over time windows of the service: a window of several days finds the departures of every
 * day in it.
 */
class TimeWindowTest {

    private static final ZoneId ZONE_ID = ZoneId.of("Europe/Zurich");
    private static final LocalDate MONDAY = LocalDate.of(2026, Month.JANUARY, 12);
    private static final LocalDate THURSDAY = MONDAY.plusDays(3);
    private static final int WEEK = 7 * 24 * 3600;

    private PublicTransitService service;

    private static OffsetDateTime at(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(ZONE_ID).toOffsetDateTime();
    }

    private static void addTrip(GtfsScheduleBuilder builder, String route, String serviceId, String from,
                                ServiceDayTime departure, String to, ServiceDayTime arrival) {
        builder.addRoute(route, "agency", route, route, RouteType.parse(3));
        builder.addTrip(route, route, serviceId, to);
        builder.addStopTime(route, from, departure, departure);
        builder.addStopTime(route, to, arrival, arrival);
    }

    /**
     * The departures from A to B in the week from Monday: at 08:00 every day, and on Thursday the fast trip at 09:00.
     */
    private static List<OffsetDateTime> departuresOfTheWeek() {
        List<OffsetDateTime> departures = new ArrayList<>();
        for (int day = 0; day < 7; day++) {
            departures.add(at(MONDAY.plusDays(day), 8, 0));
            if (day == 3) {
                departures.add(at(THURSDAY, 9, 0));
            }
        }
        return departures;
    }

    private static List<OffsetDateTime> departures(List<Connection> connections) {
        return connections.stream().map(Connection::getDepartureTime).sorted().toList();
    }

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        GtfsScheduleBuilder builder = GtfsSchedule.builder();
        builder.addAgency("agency", "Agency", "", ZONE_ID);
        builder.addCalendar("daily", EnumSet.allOf(DayOfWeek.class), MONDAY.minusDays(14), MONDAY.plusDays(21));
        builder.addCalendar("thursday", EnumSet.of(DayOfWeek.THURSDAY), MONDAY.minusDays(14), MONDAY.plusDays(21));
        // too far apart to walk
        builder.addStop("A", "A", 47.0, 8.0);
        builder.addStop("B", "B", 47.1, 8.0);
        // from A to B daily at 08:00 in 30 minutes, on Thursdays also at 09:00 in 10 minutes
        addTrip(builder, "daily", "daily", "A", ServiceDayTime.parse("08:00:00"), "B",
                ServiceDayTime.parse("08:30:00"));
        addTrip(builder, "fast", "thursday", "A", ServiceDayTime.parse("09:00:00"), "B",
                ServiceDayTime.parse("09:10:00"));
        GtfsSchedule schedule = builder.build();

        service = new PublicTransitServiceFactory(
                ServiceConfig.builder().timetableRepository(() -> schedule).build()).create();
    }

    @Test
    void connectionsOverAWeek() throws StopNotFoundException, ConnectionRoutingException {
        ConnectionQueryConfig config = ConnectionQueryConfig.builder().timeWindowDuration(WEEK).build();
        Stop a = service.getStopById("A");
        Stop b = service.getStopById("B");

        List<Connection> departing = service.getConnections(a, b, at(MONDAY, 7, 0), TimeType.DEPARTURE, config);
        List<Connection> arriving = service.getConnections(a, b, at(MONDAY.plusDays(7), 7, 0), TimeType.ARRIVAL,
                config);

        assertThat(departures(departing)).containsExactlyElementsOf(departuresOfTheWeek());
        assertThat(departures(arriving)).containsExactlyElementsOf(departuresOfTheWeek());
    }

    @Test
    void isolinesOverAWeek_takeTheFastTripOfThursday() throws StopNotFoundException, ConnectionRoutingException {
        ConnectionQueryConfig config = ConnectionQueryConfig.builder().timeWindowDuration(WEEK).build();

        Connection departing = service.getIsolines(service.getStopById("A"), at(MONDAY, 7, 0), TimeType.DEPARTURE,
                config).get(service.getStopById("B"));
        Connection arriving = service.getIsolines(service.getStopById("B"), at(MONDAY.plusDays(7), 7, 0),
                TimeType.ARRIVAL, config).get(service.getStopById("A"));

        assertThat(departing).isNotNull();
        assertThat(arriving).isNotNull();
        assertThat(departing.getDepartureTime()).isEqualTo(at(THURSDAY, 9, 0));
        assertThat(arriving.getDepartureTime()).isEqualTo(at(THURSDAY, 9, 0));
    }

}
