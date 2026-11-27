package org.naviqore.service.raptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.naviqore.gtfs.schedule.model.GtfsSchedule;
import org.naviqore.gtfs.schedule.model.GtfsScheduleBuilder;
import org.naviqore.gtfs.schedule.type.RouteType;
import org.naviqore.service.*;
import org.naviqore.service.config.ConnectionQueryConfig;
import org.naviqore.service.config.ServiceConfig;
import org.naviqore.service.exception.ConnectionRoutingException;
import org.naviqore.service.exception.StopNotFoundException;
import org.naviqore.service.walk.BeeLineWalkCalculator;
import org.naviqore.service.walk.WalkCalculator;
import org.naviqore.timetable.type.ServiceDayTime;
import org.naviqore.utils.spatial.GeoCoordinate;

import java.io.IOException;
import java.time.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Connections and isolines over time windows of the service: a window of several days finds the departures of every
 * day in it, and a connection leaves a location and reaches one at the times its window applies to, also where the
 * walk is too short to be a leg of its own.
 */
class TimeWindowTest {

    private static final ZoneId ZONE_ID = ZoneId.of("Europe/Zurich");
    private static final LocalDate MONDAY = LocalDate.of(2026, Month.JANUARY, 12);
    private static final LocalDate THURSDAY = MONDAY.plusDays(3);
    private static final int WEEK = 7 * 24 * 3600;

    private static final ConnectionQueryConfig SECOND = ConnectionQueryConfig.builder().timeWindowDuration(1).build();
    private static final GeoCoordinate STOP_A = new GeoCoordinate(47.0, 8.0);
    private static final GeoCoordinate STOP_B = new GeoCoordinate(47.1, 8.0);
    private static final int ACCESS = ServiceConfig.DEFAULT_TRANSFER_DURATION_ACCESS_EGRESS;
    private static final WalkCalculator WALK = new BeeLineWalkCalculator(ServiceConfig.DEFAULT_WALK_SPEED);

    private PublicTransitService service;

    /**
     * A location north of a stop: at it, or a walk shorter or longer than the shortest walk leg away.
     */
    private static GeoCoordinate near(GeoCoordinate stop, double latitudeOffset) {
        return new GeoCoordinate(stop.latitude() + latitudeOffset, stop.longitude());
    }

    /**
     * The time between a location and a stop: the walk and the access (egress) time.
     */
    private static int mile(GeoCoordinate location, GeoCoordinate stop) {
        return WALK.calculateWalk(location, stop).duration() + ACCESS;
    }

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
        builder.addStop("A", "A", STOP_A.latitude(), STOP_A.longitude());
        builder.addStop("B", "B", STOP_B.latitude(), STOP_B.longitude());
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

    @ParameterizedTest
    @ValueSource(doubles = {0, 0.0005, 0.0015})
    void windowOfASecond_leavesALocationInIt(double offset) throws StopNotFoundException,
            ConnectionRoutingException {
        // leaving the location at the start of the window just catches the trip at 08:00
        GeoCoordinate origin = near(STOP_A, offset);
        OffsetDateTime start = at(MONDAY, 8, 0).minusSeconds(mile(origin, STOP_A));

        List<Connection> connections = service.getConnections(origin, service.getStopById("B"), start,
                TimeType.DEPARTURE, SECOND);

        assertThat(connections).hasSize(1);
        assertThat(connections.getFirst().getDepartureTime()).isEqualTo(start);
        assertThat(connections.getFirst().getArrivalTime()).isEqualTo(at(MONDAY, 8, 30));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 0.0005, 0.0015})
    void windowOfASecond_reachesALocationInIt(double offset) throws StopNotFoundException,
            ConnectionRoutingException {
        // the trip reaching B at 08:30 reaches the location just at the end of the window
        GeoCoordinate destination = near(STOP_B, offset);
        OffsetDateTime end = at(MONDAY, 8, 30).plusSeconds(mile(destination, STOP_B));

        List<Connection> connections = service.getConnections(service.getStopById("A"), destination, end,
                TimeType.ARRIVAL, SECOND);

        assertThat(connections).hasSize(1);
        assertThat(connections.getFirst().getDepartureTime()).isEqualTo(at(MONDAY, 8, 0));
        assertThat(connections.getFirst().getArrivalTime()).isEqualTo(end);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void betweenLocations_connectionsTakeTheWayToAndFromTheStops(int window) throws ConnectionRoutingException {
        // a walk too short to be a leg of its own from the origin, and one long enough to the destination
        GeoCoordinate origin = near(STOP_A, 0.0005);
        GeoCoordinate destination = near(STOP_B, 0.0015);
        OffsetDateTime departure = at(MONDAY, 8, 0).minusSeconds(mile(origin, STOP_A));
        OffsetDateTime arrival = at(MONDAY, 8, 30).plusSeconds(mile(destination, STOP_B));
        ConnectionQueryConfig config = ConnectionQueryConfig.builder().timeWindowDuration(window).build();

        List<Connection> departing = service.getConnections(origin, destination, departure, TimeType.DEPARTURE,
                config);
        List<Connection> arriving = service.getConnections(origin, destination, arrival, TimeType.ARRIVAL, config);

        for (List<Connection> connections : List.of(departing, arriving)) {
            assertThat(connections).hasSize(1);
            Connection connection = connections.getFirst();
            assertThat(connection.getDepartureTime()).isEqualTo(departure);
            assertThat(connection.getArrivalTime()).isEqualTo(arrival);
            assertThat(connection.getLegs()).extracting(Leg::getLegType)
                    .containsExactly(LegType.PUBLIC_TRANSIT, LegType.WALK);
            assertThat(connection.getLegs().getLast()).isInstanceOfSatisfying(Walk.class,
                    walk -> assertThat(walk.getArrivalTime()).isEqualTo(arrival));
        }
    }

    @Test
    void maximumTravelDuration_countsTheWholeWay() throws ConnectionRoutingException {
        GeoCoordinate origin = near(STOP_A, 0.0005);
        GeoCoordinate destination = near(STOP_B, 0.0005);
        int travelTime = mile(origin, STOP_A) + 30 * 60 + mile(destination, STOP_B);

        for (int maximum : new int[]{travelTime, travelTime - 1}) {
            ConnectionQueryConfig config = ConnectionQueryConfig.builder().maximumTravelDuration(maximum).build();
            List<Connection> connections = service.getConnections(origin, destination, at(MONDAY, 7, 50),
                    TimeType.DEPARTURE, config);

            assertThat(connections).hasSize(maximum == travelTime ? 1 : 0);
        }
    }

    @Test
    void isolinesOverAWindowOfASecond_leaveAndReachTheLocationInIt() throws StopNotFoundException,
            ConnectionRoutingException {
        GeoCoordinate origin = near(STOP_A, 0.0005);
        GeoCoordinate destination = near(STOP_B, 0.0005);
        OffsetDateTime start = at(MONDAY, 8, 0).minusSeconds(mile(origin, STOP_A));
        OffsetDateTime end = at(MONDAY, 8, 30).plusSeconds(mile(destination, STOP_B));

        Connection departing = service.getIsolines(origin, start, TimeType.DEPARTURE, SECOND)
                .get(service.getStopById("B"));
        Connection arriving = service.getIsolines(destination, end, TimeType.ARRIVAL, SECOND)
                .get(service.getStopById("A"));

        assertThat(departing).isNotNull();
        assertThat(arriving).isNotNull();
        assertThat(departing.getDepartureTime()).isEqualTo(start);
        assertThat(arriving.getArrivalTime()).isEqualTo(end);
    }

}
