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
 * The connections of an isoline that ride the same trip between the same stops share that ride, which is mapped once,
 * and each rides on from it on legs of its own.
 */
class IsolineRidesTest {

    private static final ZoneId ZONE_ID = ZoneId.of("Europe/Zurich");
    private static final LocalDate DATE = LocalDate.of(2026, Month.JANUARY, 14);

    private PublicTransitService service;

    private static OffsetDateTime at(String time) {
        return DATE.atTime(LocalTime.parse(time)).atZone(ZONE_ID).toOffsetDateTime();
    }

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        GtfsScheduleBuilder builder = GtfsSchedule.builder();
        builder.addAgency("agency", "Agency", "", ZONE_ID);
        builder.addCalendar("daily", EnumSet.allOf(DayOfWeek.class), DATE.minusDays(7), DATE.plusDays(7));
        // too far apart to walk
        builder.addStop("A", "A", 47.0, 8.0);
        builder.addStop("B", "B", 47.1, 8.0);
        builder.addStop("C", "C", 47.2, 8.0);
        builder.addStop("D", "D", 47.3, 8.0);
        builder.addRoute("x", "agency", "x", "x", RouteType.parse(3));
        builder.addRoute("y", "agency", "y", "y", RouteType.parse(3));
        // x runs A - B at 08:00, y runs B - C - D at 08:20
        builder.addTrip("x", "x", "daily", "B");
        builder.addStopTime("x", "A", ServiceDayTime.parse("08:00:00"), ServiceDayTime.parse("08:00:00"));
        builder.addStopTime("x", "B", ServiceDayTime.parse("08:10:00"), ServiceDayTime.parse("08:10:00"));
        builder.addTrip("y", "y", "daily", "D");
        builder.addStopTime("y", "B", ServiceDayTime.parse("08:20:00"), ServiceDayTime.parse("08:20:00"));
        builder.addStopTime("y", "C", ServiceDayTime.parse("08:30:00"), ServiceDayTime.parse("08:30:00"));
        builder.addStopTime("y", "D", ServiceDayTime.parse("08:40:00"), ServiceDayTime.parse("08:40:00"));
        GtfsSchedule schedule = builder.build();

        service = new PublicTransitServiceFactory(
                ServiceConfig.builder().timetableRepository(() -> schedule).build()).create();
    }

    @Test
    void shouldShareRideOfConnectionsRidingTheSameTrip() throws StopNotFoundException, ConnectionRoutingException {
        Map<Stop, Connection> isolines = service.getIsolines(service.getStopById("A"), at("08:00"), TimeType.DEPARTURE,
                ConnectionQueryConfig.defaults());

        Leg toB = isolines.get(service.getStopById("B")).getLegs().getFirst();
        assertThat(toB).isInstanceOf(PublicTransitLeg.class);
        assertThat(((PublicTransitLeg) toB).getTrip().getId()).isEqualTo("x");
        assertThat(isolines.get(service.getStopById("C")).getLegs().getFirst()).isSameAs(toB);
        assertThat(isolines.get(service.getStopById("D")).getLegs().getFirst()).isSameAs(toB);
    }

    @Test
    void shouldRideOnOnLegsOfTheirOwn() throws StopNotFoundException, ConnectionRoutingException {
        Map<Stop, Connection> isolines = service.getIsolines(service.getStopById("A"), at("08:00"), TimeType.DEPARTURE,
                ConnectionQueryConfig.defaults());

        Connection toC = isolines.get(service.getStopById("C"));
        Connection toD = isolines.get(service.getStopById("D"));
        PublicTransitLeg rideToC = (PublicTransitLeg) toC.getLegs().getLast();
        PublicTransitLeg rideToD = (PublicTransitLeg) toD.getLegs().getLast();
        assertThat(rideToC.getTrip().getId()).isEqualTo("y");
        assertThat(rideToC.getArrival().getStop().getId()).isEqualTo("C");
        assertThat(toC.getArrivalTime()).isEqualTo(at("08:30"));
        assertThat(rideToD.getTrip().getId()).isEqualTo("y");
        assertThat(rideToD.getArrival().getStop().getId()).isEqualTo("D");
        assertThat(toD.getArrivalTime()).isEqualTo(at("08:40"));
        // both legs call at the stops of the same mapped trip
        assertThat(rideToD.getTrip()).isSameAs(rideToC.getTrip());
    }

}
