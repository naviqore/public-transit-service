package org.naviqore.timetable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.naviqore.timetable.RoutePartitioner.SubRoute;
import org.naviqore.timetable.model.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.*;

class RoutePartitionerTest {

    private Stop a;
    private Stop b;
    private Stop c;
    private Route route1;
    private Route route2;
    private Trip trip1;
    private Trip trip2;
    private Trip trip3;
    private Trip trip4;
    private RoutePartitioner partitioner;

    private static Stop stop(String id) {
        Stop stop = mock(Stop.class);
        when(stop.getId()).thenReturn(id);
        return stop;
    }

    private static Route route(String id, Trip... trips) {
        Route route = mock(Route.class);
        when(route.getId()).thenReturn(id);
        doReturn(List.of(trips)).when(route).getTrips();
        return route;
    }

    private static Trip trip(Stop... stops) {
        Trip trip = mock(Trip.class);
        List<StopTime> stopTimes = Arrays.stream(stops).map(stop -> {
            StopTime stopTime = mock(StopTime.class);
            when(stopTime.stop()).thenReturn(stop);
            return stopTime;
        }).toList();
        doReturn(stopTimes).when(trip).getStopTimes();
        return trip;
    }

    @BeforeEach
    void setUp() {
        a = stop("A");
        b = stop("B");
        c = stop("C");
        trip1 = trip(a, b, c);
        trip2 = trip(a, b, c);
        trip3 = trip(a, c);
        trip4 = trip(c, b, a);
        route1 = route("route1", trip1, trip2, trip3);
        route2 = route("route2", trip4);
        for (Trip trip : List.of(trip1, trip2, trip3)) {
            when(trip.getRoute()).thenReturn(route1);
        }
        when(trip4.getRoute()).thenReturn(route2);

        Timetable timetable = mock(Timetable.class);
        doReturn(Map.of("route1", route1, "route2", route2)).when(timetable).getRoutes();
        partitioner = new RoutePartitioner(timetable);
    }

    @Test
    void getSubRoutes() {
        assertThat(partitioner.getSubRoutes(route1)).extracting(SubRoute::getId, SubRoute::getTrips,
                        SubRoute::getStopsSequence)
                .containsExactlyInAnyOrder(tuple("route1_sr1", List.of(trip1, trip2), List.of(a, b, c)),
                        tuple("route1_sr2", List.of(trip3), List.of(a, c)));
        assertThat(partitioner.getSubRoutes(route2)).extracting(SubRoute::getId, SubRoute::getTrips,
                SubRoute::getStopsSequence).containsExactly(tuple("route2_sr1", List.of(trip4), List.of(c, b, a)));
    }

    @Test
    void getSubRoute() {
        assertThat(partitioner.getSubRoute(trip1)).isNotNull().isSameAs(partitioner.getSubRoute(trip2));
        assertThat(partitioner.getSubRoute(trip3)).isNotNull().extracting(SubRoute::getId).isEqualTo("route1_sr2");
        assertThat(partitioner.getSubRoute(trip4)).isNotNull().extracting(SubRoute::getRoute).isSameAs(route2);
    }
}
