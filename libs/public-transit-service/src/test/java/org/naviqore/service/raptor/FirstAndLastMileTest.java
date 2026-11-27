package org.naviqore.service.raptor;

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
import org.naviqore.timetable.type.ServiceDayTime;
import org.naviqore.utils.spatial.GeoCoordinate;

import java.io.IOException;
import java.time.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The walks at either end of a connection, from or to a location or a stop without departures or arrivals: each takes
 * the access or egress time at its stop, and is a leg of its own only if it takes longer than the walk duration
 * minimum, which counts no access or egress time.
 */
class FirstAndLastMileTest {

    private static final ZoneId ZONE_ID = ZoneId.of("Europe/Zurich");
    private static final LocalDate MONDAY = LocalDate.of(2026, Month.JANUARY, 12);
    private static final GeoCoordinate STOP_A = new GeoCoordinate(47.0, 8.0);
    private static final GeoCoordinate STOP_B = new GeoCoordinate(47.1, 8.0);
    // as far north of A and B, where the stops X and Y have no departures or arrivals
    private static final GeoCoordinate NEAR_A = new GeoCoordinate(47.0005, 8.0);
    private static final GeoCoordinate NEAR_B = new GeoCoordinate(47.1005, 8.0);
    private static final int WALK = new BeeLineWalkCalculator(ServiceConfig.DEFAULT_WALK_SPEED).calculateWalk(NEAR_A,
            STOP_A).duration();

    private static PublicTransitService service(int walkDurationMinimum) throws IOException, InterruptedException {
        GtfsScheduleBuilder builder = GtfsSchedule.builder();
        builder.addAgency("agency", "Agency", "", ZONE_ID);
        builder.addCalendar("daily", EnumSet.allOf(DayOfWeek.class), MONDAY.minusDays(7), MONDAY.plusDays(7));
        builder.addStop("A", "A", STOP_A.latitude(), STOP_A.longitude());
        builder.addStop("B", "B", STOP_B.latitude(), STOP_B.longitude());
        builder.addStop("X", "X", NEAR_A.latitude(), NEAR_A.longitude());
        builder.addStop("Y", "Y", NEAR_B.latitude(), NEAR_B.longitude());
        builder.addRoute("R", "agency", "R", "R", RouteType.parse(3));
        builder.addTrip("T", "R", "daily", "B");
        builder.addStopTime("T", "A", ServiceDayTime.parse("08:00:00"), ServiceDayTime.parse("08:00:00"));
        builder.addStopTime("T", "B", ServiceDayTime.parse("08:30:00"), ServiceDayTime.parse("08:30:00"));
        GtfsSchedule schedule = builder.build();

        return new PublicTransitServiceFactory(ServiceConfig.builder()
                .timetableRepository(() -> schedule)
                .walkDurationMinimum(walkDurationMinimum)
                .build()).create();
    }

    private static OffsetDateTime departureTime(Leg leg) {
        return switch (leg) {
            case Walk walk -> walk.getDepartureTime();
            case Transfer transfer -> transfer.getDepartureTime();
            case PublicTransitLeg ride -> ride.getDeparture().getDepartureTime();
            default -> throw new IllegalArgumentException("Unexpected leg: " + leg);
        };
    }

    private static OffsetDateTime arrivalTime(Leg leg) {
        return switch (leg) {
            case Walk walk -> walk.getArrivalTime();
            case Transfer transfer -> transfer.getArrivalTime();
            case PublicTransitLeg ride -> ride.getArrival().getArrivalTime();
            default -> throw new IllegalArgumentException("Unexpected leg: " + leg);
        };
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void walk_isALegOfItsOwnOnlyIfLongerThanTheMinimum(
            int shorterMinimum) throws IOException, InterruptedException, StopNotFoundException, ConnectionRoutingException {
        PublicTransitService service = service(WALK - shorterMinimum);
        OffsetDateTime departure = MONDAY.atTime(7, 50).atZone(ZONE_ID).toOffsetDateTime();
        ConnectionQueryConfig config = ConnectionQueryConfig.defaults();

        List<Connection> connections = new ArrayList<>(
                service.getConnections(NEAR_A, NEAR_B, departure, TimeType.DEPARTURE, config));
        connections.addAll(service.getConnections(service.getStopById("X"), service.getStopById("Y"), departure,
                TimeType.DEPARTURE, config));

        List<LegType> legs = shorterMinimum == 0 ? List.of(LegType.PUBLIC_TRANSIT) : List.of(LegType.WALK,
                LegType.PUBLIC_TRANSIT, LegType.WALK);
        assertThat(connections).hasSize(2)
                .allSatisfy(connection -> assertThat(connection.getLegs()).extracting(Leg::getLegType)
                        .containsExactlyElementsOf(legs));
    }

    @Test
    void walk_meetsItsTripWithTheAccessOrEgressTime() throws IOException, InterruptedException, StopNotFoundException, ConnectionRoutingException {
        PublicTransitService service = service(0);
        OffsetDateTime departure = MONDAY.atTime(7, 50).atZone(ZONE_ID).toOffsetDateTime();
        ConnectionQueryConfig config = ConnectionQueryConfig.defaults();

        List<Connection> connections = new ArrayList<>(
                service.getConnections(NEAR_A, NEAR_B, departure, TimeType.DEPARTURE, config));
        connections.addAll(service.getConnections(service.getStopById("X"), service.getStopById("Y"), departure,
                TimeType.DEPARTURE, config));

        int walk = WALK + ServiceConfig.DEFAULT_TRANSFER_DURATION_ACCESS_EGRESS;
        OffsetDateTime tripDeparture = MONDAY.atTime(8, 0).atZone(ZONE_ID).toOffsetDateTime();
        OffsetDateTime tripArrival = MONDAY.atTime(8, 30).atZone(ZONE_ID).toOffsetDateTime();
        assertThat(connections).hasSize(2).allSatisfy(connection -> {
            List<Leg> legs = connection.getLegs();
            assertThat(legs).hasSize(3).extracting(Leg::getDuration).containsExactly(walk, 30 * 60, walk);
            assertThat(legs).extracting(FirstAndLastMileTest::departureTime)
                    .containsExactly(tripDeparture.minusSeconds(walk), tripDeparture, tripArrival);
            assertThat(legs).extracting(FirstAndLastMileTest::arrivalTime)
                    .containsExactly(tripDeparture, tripArrival, tripArrival.plusSeconds(walk));
        });
    }
}
