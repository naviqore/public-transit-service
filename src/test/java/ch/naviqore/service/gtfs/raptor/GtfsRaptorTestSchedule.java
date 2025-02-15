package ch.naviqore.service.gtfs.raptor;

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
 *     |--------B1------------C1-----------D1
 *     |
 * A---|       (B)      |-----C-------|   (D)           (E)
 *     |                |             |
 *     |--------B2 -----|    (C2)     |----D2
 * </pre>
 * <ul>
 *     <li><b>Route 1</b>: Passes through A - B1 - C1 - D1</li>
 *     <li><b>Route 2</b>: Passes through A - B2 - C  - D2</li>
 * </ul>
 * Stops B, C2, D, E have no departures/arrivals and should not be included in the raptor conversion.
 * Stops B, C, D are parents of stops (B1, B2), (C1, C2) and (D1, D2) respectively.
 */
public class GtfsRaptorTestSchedule {

    private final GtfsScheduleBuilder builder = GtfsSchedule.builder();

    public GtfsRaptorTestSchedule() {
        setup();
    }

    private void setup() {
        builder.addCalendar("always", EnumSet.allOf(DayOfWeek.class), LocalDate.MIN, LocalDate.MAX);
        builder.addAgency("agency", "Some Agency", "", "America/New_York");

        builder.addStop("A", "A", 0.0, 0.0);
        builder.addStop("B", "B", 0.0, 1.0);
        builder.addStop("B1", "B1", 0.001, 1.0, "B", AccessibilityInformation.UNKNOWN);
        builder.addStop("B2", "B2", -0.001, 1.0, "B", AccessibilityInformation.UNKNOWN);
        builder.addStop("C", "C", 0.0, 2.0);
        builder.addStop("C1", "C1", 0.001, 2.0, "C", AccessibilityInformation.UNKNOWN);
        builder.addStop("C2", "C2", -0.001, 2.0, "C", AccessibilityInformation.UNKNOWN);
        // D, D1 and D2 are more than 500 m apart!
        builder.addStop("D", "D", 0.0, 3.0);
        builder.addStop("D1", "D1", 0.005, 3.0, "D", AccessibilityInformation.UNKNOWN);
        builder.addStop("D2", "D2", -0.005, 3.0, "D", AccessibilityInformation.UNKNOWN);
        builder.addStop("E", "E", 0.0, 4.0);

        // Route 1 goes from A, B1, C1, D2
        builder.addRoute("R1", "agency", "R1", "R1", RouteType.parse(1));
        builder.addTrip("T1", "R1", "always", "D1");
        builder.addStopTime("T1", "A", new ServiceDayTime(60), new ServiceDayTime(120));
        builder.addStopTime("T1", "B1", new ServiceDayTime(180), new ServiceDayTime(240));
        builder.addStopTime("T1", "C1", new ServiceDayTime(300), new ServiceDayTime(360));
        builder.addStopTime("T1", "D1", new ServiceDayTime(420), new ServiceDayTime(480));

        // Route 2 goes from A, B2, C
        builder.addRoute("R2", "agency", "R2", "R2", RouteType.parse(1));
        builder.addTrip("T2", "R2", "always", "D2");
        builder.addStopTime("T2", "A", new ServiceDayTime(60), new ServiceDayTime(120));
        builder.addStopTime("T2", "B2", new ServiceDayTime(180), new ServiceDayTime(240));
        builder.addStopTime("T2", "C", new ServiceDayTime(300), new ServiceDayTime(360));
        builder.addStopTime("T2", "D2", new ServiceDayTime(420), new ServiceDayTime(480));
    }

    public void addTransfer(String fromStopId, String toStopId, TransferType type, int duration) {
        builder.addTransfer(fromStopId, toStopId, type, duration);
    }

    public GtfsSchedule build() {
        return builder.build();
    }
}
