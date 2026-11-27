package org.naviqore.gtfs.schedule.model;

import org.junit.jupiter.api.Test;
import org.naviqore.gtfs.schedule.type.RouteType;
import org.naviqore.timetable.type.PickupDropOffType;
import org.naviqore.timetable.type.ServiceDayTime;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StopTimeTest {

    @Test
    void shouldHoldEveryPairOfPickupAndDropOffTypes() {
        PickupDropOffType[] types = PickupDropOffType.values();
        GtfsScheduleBuilder builder = GtfsSchedule.builder();
        builder.addAgency("agency", "Agency", "", ZoneId.of("Europe/Zurich"));
        builder.addCalendar("daily", EnumSet.allOf(DayOfWeek.class), LocalDate.MIN, LocalDate.MAX);
        builder.addRoute("route", "agency", "route", "route", RouteType.parse(3));
        builder.addTrip("trip", "route", "daily", "");
        // a stop time for each pair, a minute apart
        for (int i = 0; i < types.length * types.length; i++) {
            builder.addStop("stop" + i, "stop" + i, 47.0, 8.0 + i * 0.01);
            builder.addStopTime("trip", "stop" + i, new ServiceDayTime(60 * i), new ServiceDayTime(60 * i),
                    types[i / types.length], types[i % types.length]);
        }

        List<StopTime> stopTimes = builder.build().getTrips().get("trip").getStopTimes();

        assertThat(stopTimes).hasSize(types.length * types.length);
        for (int i = 0; i < stopTimes.size(); i++) {
            assertThat(stopTimes.get(i).pickupType()).isEqualTo(types[i / types.length]);
            assertThat(stopTimes.get(i).dropOffType()).isEqualTo(types[i % types.length]);
        }
    }

}
