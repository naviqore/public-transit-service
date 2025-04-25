package org.naviqore.gtfs.schedule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.naviqore.gtfs.schedule.model.*;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(GtfsScheduleTestExtension.class)
class GtfsRoutePartitionerTest {

    private GtfsSchedule schedule;
    private GtfsRoutePartitioner partitioner;

    @BeforeEach
    void setUp(GtfsScheduleTestBuilder builder) {
        schedule = builder.withAddAgency()
                .withAddCalendars()
                .withAddCalendarDates()
                .withAddInterCity()
                .withAddUnderground()
                .withAddBus()
                .build();
        partitioner = new GtfsRoutePartitioner(schedule);
    }

    @Test
    void getSubRoutes() {
        assertThat(partitioner.getSubRoutes(schedule.getRoutes().get("route1"))).as("SubRoutes").hasSize(2);
        assertThat(partitioner.getSubRoutes(schedule.getRoutes().get("route2"))).as("SubRoutes").hasSize(1);
        assertThat(partitioner.getSubRoutes(schedule.getRoutes().get("route3"))).as("SubRoutes").hasSize(2);
    }

    @Test
    void getSubRoute() {
        for (Route route : schedule.getRoutes().values()) {
            for (Trip trip : route.getTrips()) {
                GtfsRoutePartitioner.SubRoute subRoute = partitioner.getSubRoute(trip);
                assertThat(subRoute).as("SubRoute for trip ID " + trip.getId() + " in route " + route.getId())
                        .isNotNull();
            }
        }
    }
}