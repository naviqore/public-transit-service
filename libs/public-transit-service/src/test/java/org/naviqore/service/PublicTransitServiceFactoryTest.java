package org.naviqore.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.naviqore.service.config.ConnectionQueryConfig;
import org.naviqore.service.config.ServiceConfig;
import org.naviqore.service.exception.*;
import org.naviqore.service.raptor.InMemoryTimetable;

import java.io.IOException;
import java.time.LocalDate;
import java.time.Month;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Builds the public transit service from a timetable that no schedule format provides, so the service relies on the
 * timetable interfaces alone.
 */
class PublicTransitServiceFactoryTest {

    private static final LocalDate DATE = LocalDate.of(2026, Month.MARCH, 2);

    private PublicTransitService service;

    private static OffsetDateTime at(int hour, int minute) {
        return DATE.atTime(hour, minute).atZone(InMemoryTimetable.ZONE_ID).toOffsetDateTime();
    }

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        InMemoryTimetable timetable = new InMemoryTimetable();
        ServiceConfig config = ServiceConfig.builder().timetableRepository(() -> timetable).build();
        service = new PublicTransitServiceFactory(config).create();
    }

    @Test
    void shouldReportValidityOfTimetable() {
        assertThat(service.getValidity().getStartDate()).isEqualTo(InMemoryTimetable.START_DATE);
        assertThat(service.getValidity().getEndDate()).isEqualTo(InMemoryTimetable.END_DATE);
    }

    @Test
    void shouldSearchStops() {
        assertThat(service.getStops("alp", SearchType.STARTS_WITH, StopSortStrategy.ALPHABETICAL)).extracting(
                Stop::getId).containsExactly("A");
    }

    @Test
    void shouldRouteConnection() throws StopNotFoundException, ConnectionRoutingException {
        List<Connection> connections = service.getConnections(service.getStopById("A"), service.getStopById("C"),
                at(7, 55), TimeType.DEPARTURE, ConnectionQueryConfig.builder().build());

        assertThat(connections).isNotEmpty();
        Connection connection = connections.getFirst();
        assertThat(connection.getDepartureTime()).isEqualTo(at(8, 0));
        assertThat(connection.getArrivalTime()).isEqualTo(at(8, 20));
        assertThat(connection.getLegs()).singleElement().isInstanceOfSatisfying(PublicTransitLeg.class, leg -> {
            assertThat(leg.getTrip().getId()).isEqualTo("T1");
            assertThat(leg.getTrip().getRoute().getRouteType()).isEqualTo(TravelMode.TRAM);
        });
    }

    @Test
    void shouldListDepartures() throws StopNotFoundException {
        List<StopTime> departures = service.getStopTimes(service.getStopById("B"), at(7, 0), at(10, 0),
                TimeType.DEPARTURE, StopScope.STRICT);

        assertThat(departures).extracting(StopTime::getDepartureTime).containsExactly(at(8, 11), at(9, 11));
    }

    @Test
    void shouldFindTripAndRoute() throws TripNotFoundException, TripNotActiveException, RouteNotFoundException {
        Trip trip = service.getTripById("T2", DATE);

        assertThat(trip.getHeadSign()).isEqualTo("Charlie");
        assertThat(trip.getStopTimes()).extracting(stopTime -> stopTime.getStop().getId())
                .containsExactly("A", "B", "C");

        Route route = service.getRouteById("R1");

        assertThat(route.getShortName()).isEqualTo("1");
        assertThat(route.getAgency()).isEqualTo("Example Transit");
    }

}
