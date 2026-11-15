package org.naviqore.service.raptor.convert;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.naviqore.gtfs.schedule.model.GtfsSchedule;
import org.naviqore.gtfs.schedule.model.GtfsScheduleBuilder;
import org.naviqore.gtfs.schedule.type.RouteType;
import org.naviqore.raptor.*;
import org.naviqore.raptor.router.RaptorConfig;
import org.naviqore.timetable.model.Stop;
import org.naviqore.timetable.type.AccessibilityInformation;
import org.naviqore.timetable.type.PickupDropOffType;
import org.naviqore.timetable.type.ServiceDayTime;
import org.naviqore.timetable.type.TransferType;

import java.time.*;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The transfers of a timetable apply to the router as GTFS defines them: for all trips between their stops or at their
 * station, and for particular routes or trips; and a trip is boarded only where it picks up passengers.
 */
class TimetableToRaptorConverterTransfersTest {

    private static final ZoneId ZONE_ID = ZoneId.of("Europe/Zurich");
    private static final LocalDate DATE = LocalDate.of(2026, Month.JANUARY, 14);
    private static final QueryConfig QUERY_CONFIG = QueryConfig.defaults();

    private GtfsScheduleBuilder builder;

    private static OffsetDateTime at(String time) {
        return DATE.atTime(LocalTime.parse(time)).atZone(ZONE_ID).toOffsetDateTime();
    }

    private static ServiceDayTime time(String time) {
        return ServiceDayTime.parse(time + ":00");
    }

    private static List<String> tripIds(Connection connection) {
        return connection.getRouteLegs().stream().map(Leg::getTripId).toList();
    }

    /**
     * Generates a walk of a minute between the platforms of the station S, as a walk transfer generator would.
     */
    private static List<TransferGenerator.Transfer> walkBetweenPlatforms(java.util.Collection<Stop> stops) {
        Map<String, Stop> stopsById = stops.stream().collect(Collectors.toMap(Stop::getId, Function.identity()));
        return List.of(new TransferGenerator.Transfer(stopsById.get("S1"), stopsById.get("S2"), 60));
    }

    private static List<Connection> routeFromA(RaptorAlgorithm raptor, String target, QueryConfig config) {
        return raptor.connectionQuery(Map.of("A", at("08:00")), Map.of(target, 0), TimeType.DEPARTURE, config);
    }

    /**
     * Adds a trip of a route from one stop to another.
     */
    private void addTrip(String routeId, String tripId, String from, String departure, String to, String arrival) {
        builder.addTrip(tripId, routeId, "daily", to);
        builder.addStopTime(tripId, from, time(departure), time(departure));
        builder.addStopTime(tripId, to, time(arrival), time(arrival));
    }

    private RaptorAlgorithm convert(TransferGenerator... transferGenerators) {
        return new TimetableToRaptorConverter(RaptorConfig.defaults(), builder.build(),
                List.of(transferGenerators)).run();
    }

    /**
     * The station S has the platforms S1 and S2; the other stops are too far apart to walk:
     * <ul>
     *     <li>R1 runs from A to S1, arriving at 08:10, where its vehicle continues as R3 to V.</li>
     *     <li>R2 runs from S2 to T at 08:15 and 08:45, and at 08:16 to U, a sub-route of its own.</li>
     * </ul>
     */
    @BeforeEach
    void setUp() {
        builder = GtfsSchedule.builder();
        builder.addAgency("agency", "Agency", "", ZONE_ID);
        builder.addCalendar("daily", EnumSet.allOf(DayOfWeek.class), DATE.minusDays(7), DATE.plusDays(7));
        builder.addStop("A", "A", 47.0, 8.0);
        builder.addStop("S", "S", 47.1, 8.0);
        builder.addStop("S1", "S1", 47.1, 8.0001, "S", AccessibilityInformation.UNKNOWN);
        builder.addStop("S2", "S2", 47.1, 7.9999, "S", AccessibilityInformation.UNKNOWN);
        builder.addStop("T", "T", 47.2, 8.0);
        builder.addStop("U", "U", 47.2, 8.1);
        builder.addStop("V", "V", 47.1, 8.2);
        for (String routeId : new String[]{"R1", "R2", "R3"}) {
            builder.addRoute(routeId, "agency", routeId, routeId, RouteType.parse(3));
        }
        addTrip("R1", "R1-0", "A", "08:00", "S1", "08:10");
        addTrip("R3", "R3-0", "S1", "08:10", "V", "08:30");
        addTrip("R2", "R2-0", "S2", "08:15", "T", "08:30");
        addTrip("R2", "R2-1", "S2", "08:45", "T", "09:00");
        addTrip("R2", "R2-2", "S2", "08:16", "U", "08:20");
    }

    @Test
    void aStationThatRefusesTransfers_refusesThemBetweenItsStops() {
        builder.addTransfer("S", "S", TransferType.NOT_POSSIBLE, null);
        RaptorAlgorithm raptor = convert(TimetableToRaptorConverterTransfersTest::walkBetweenPlatforms);

        assertThat(routeFromA(raptor, "T", QUERY_CONFIG)).isEmpty();
    }

    @Test
    void aTransferBetweenStops_overridesTheOneOfTheirStation() {
        // the walk takes the time of the transfer of the platforms, and is not refused by the one of the station
        builder.addTransfer("S", "S", TransferType.NOT_POSSIBLE, null);
        builder.addTransfer("S1", "S2", TransferType.MINIMUM_TIME, 600);
        RaptorAlgorithm raptor = convert(TimetableToRaptorConverterTransfersTest::walkBetweenPlatforms);

        List<Connection> connections = routeFromA(raptor, "T", QUERY_CONFIG);
        assertThat(connections).hasSize(1);
        assertThat(tripIds(connections.getFirst())).containsExactly("R1-0", "R2-1");
    }

    @Test
    void aTimedTransfer_takesNoTimeButTheMinimumTransferDuration() {
        builder.addTransfer("S1", "S2", TransferType.TIMED, null);
        RaptorAlgorithm raptor = convert();

        assertThat(routeFromA(raptor, "T", QUERY_CONFIG).getFirst().getArrivalTime()).isEqualTo(at("08:30"));

        // six minutes after 08:10, the trip at 08:15 has left
        QueryConfig config = QueryConfig.builder().minimumTransferDuration(360).build();
        assertThat(routeFromA(raptor, "T", config).getFirst().getArrivalTime()).isEqualTo(at("09:00"));
    }

    @Test
    void anInSeatTransfer_continuesTheTrip() {
        // without stops, from the last stop of R1-0 to the first of R3-0; the vehicle waits for no transfer duration
        builder.addTransfer(null, null, null, null, "R1-0", "R3-0", TransferType.IN_SEAT, null);
        RaptorAlgorithm raptor = convert();

        QueryConfig config = QueryConfig.builder().minimumTransferDuration(300).build();
        List<Connection> connections = routeFromA(raptor, "V", config);
        assertThat(connections).hasSize(1);
        assertThat(tripIds(connections.getFirst())).containsExactly("R1-0", "R3-0");
        assertThat(connections.getFirst().getArrivalTime()).isEqualTo(at("08:30"));
    }

    @Test
    void anInSeatTransfer_isNeededToStayOnTheVehicle() {
        // without it, the change at S1 takes the same stop transfer time, and R3-0 has left
        RaptorAlgorithm raptor = convert();

        assertThat(routeFromA(raptor, "V", QUERY_CONFIG)).isEmpty();
    }

    @Test
    void aTransferBetweenRoutes_appliesToEverySubRouteOfTheRoutes() {
        // there is no walk between the platforms, but a transfer from R1 to R2
        builder.addTransfer("S1", "S2", "R1", "R2", null, null, TransferType.MINIMUM_TIME, 60);
        RaptorAlgorithm raptor = convert();

        assertThat(routeFromA(raptor, "T", QUERY_CONFIG).getFirst().getArrivalTime()).isEqualTo(at("08:30"));
        assertThat(routeFromA(raptor, "U", QUERY_CONFIG).getFirst().getArrivalTime()).isEqualTo(at("08:20"));
    }

    @Test
    void aTransferBetweenTripsThatIsNotPossible_leavesTheOtherTripsOfTheRoute() {
        builder.addTransfer("S1", "S2", TransferType.MINIMUM_TIME, 60);
        builder.addTransfer("S1", "S2", "R1", "R2", "R1-0", "R2-0", TransferType.NOT_POSSIBLE, null);
        RaptorAlgorithm raptor = convert();

        List<Connection> connections = routeFromA(raptor, "T", QUERY_CONFIG);
        assertThat(connections).hasSize(1);
        assertThat(tripIds(connections.getFirst())).containsExactly("R1-0", "R2-1");
        assertThat(routeFromA(raptor, "U", QUERY_CONFIG).getFirst().getArrivalTime()).isEqualTo(at("08:20"));
    }

    @Test
    void aTripThatDoesNotPickUpPassengersAtAStop_isNotBoardedThere() {
        // R2-3 would reach T first, but only sets down passengers at S2
        builder.addTrip("R2-3", "R2", "daily", "T");
        builder.addStopTime("R2-3", "S2", time("08:12"), time("08:12"), PickupDropOffType.NOT_AVAILABLE,
                PickupDropOffType.REGULAR);
        builder.addStopTime("R2-3", "T", time("08:20"), time("08:20"));
        RaptorAlgorithm raptor = convert(TimetableToRaptorConverterTransfersTest::walkBetweenPlatforms);

        List<Connection> connections = routeFromA(raptor, "T", QUERY_CONFIG);
        assertThat(connections).hasSize(1);
        assertThat(tripIds(connections.getFirst())).containsExactly("R1-0", "R2-0");
    }

}
