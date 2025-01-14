package ch.naviqore.service.gtfs.raptor.convert;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.model.GtfsScheduleBuilder;
import ch.naviqore.gtfs.schedule.type.AccessibilityInformation;
import ch.naviqore.gtfs.schedule.type.RouteType;
import ch.naviqore.gtfs.schedule.type.ServiceDayTime;
import ch.naviqore.gtfs.schedule.type.TransferType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;

/**
 * GTFS schedule builder for testing. Builds a schedule as below
 * <pre>
 *     |--------B1------------C1
 *     |
 * A---|       (B)      |-----C           (D)
 *     |                |
 *     |--------B2 -----|    (C2)
 * </pre>
 * <ul>
 *     <li><b>Route 1</b>: Passes through A - B1 - C1</li>
 *     <li><b>Route 2</b>: Passes through A - B2 - C</li>
 * </ul>
 * Stops B, C2 and D have no departures/arrivals and should not be included in the raptor conversion.
 * Stops B and C are parents of stops B1, B2 and C1, C2, respectively.
 */
public class GtfsToRaptorTestSchedule {

    private final GtfsScheduleBuilder builder = GtfsSchedule.builder();

    public GtfsToRaptorTestSchedule() {
        setup();
    }

    private void setup() {
        builder.addCalendar("always", EnumSet.allOf(DayOfWeek.class), LocalDate.MIN, LocalDate.MAX);
        builder.addAgency("agency", "Some Agency", "", "America/New_York");

        builder.addStop("A", "A", 0.0, 0.0);
        builder.addStop("B", "B", 0.0, 0.0);
        builder.addStop("B1", "B1", 0.0, 0.0, "B", AccessibilityInformation.UNKNOWN);
        builder.addStop("B2", "B2", 0.0, 0.0, "B", AccessibilityInformation.UNKNOWN);
        builder.addStop("C", "C", 0.0, 0.0);
        builder.addStop("C1", "C1", 0.0, 0.0, "C", AccessibilityInformation.UNKNOWN);
        builder.addStop("C2", "C2", 0.0, 0.0, "C", AccessibilityInformation.UNKNOWN);
        builder.addStop("D", "D", 0.0, 0.0);

        // Route 1 goes from A, B1, C1
        builder.addRoute("R1", "agency", "R1", "R1", RouteType.parse(1));
        builder.addTrip("T1", "R1", "always", "C1");
        builder.addStopTime("T1", "A", new ServiceDayTime(0), new ServiceDayTime(0));
        builder.addStopTime("T1", "B1", new ServiceDayTime(0), new ServiceDayTime(0));
        builder.addStopTime("T1", "C1", new ServiceDayTime(0), new ServiceDayTime(0));

        // Route 2 goes from A, B2, C
        builder.addRoute("R2", "agency", "R2", "R2", RouteType.parse(1));
        builder.addTrip("T2", "R2", "always", "C");
        builder.addStopTime("T2", "A", new ServiceDayTime(0), new ServiceDayTime(0));
        builder.addStopTime("T2", "B2", new ServiceDayTime(0), new ServiceDayTime(0));
        builder.addStopTime("T2", "C", new ServiceDayTime(0), new ServiceDayTime(0));
    }

    public void addTransfer(String fromStopId, String toStopId, TransferType type, int duration) {
        builder.addTransfer(fromStopId, toStopId, type, duration);
    }

    public GtfsSchedule build() {
        return builder.build();
    }
}
