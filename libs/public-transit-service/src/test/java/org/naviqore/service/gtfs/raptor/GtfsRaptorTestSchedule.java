package org.naviqore.service.gtfs.raptor;

import org.naviqore.gtfs.schedule.model.GtfsSchedule;
import org.naviqore.gtfs.schedule.model.GtfsScheduleBuilder;
import org.naviqore.gtfs.schedule.type.AccessibilityInformation;
import org.naviqore.gtfs.schedule.type.RouteType;
import org.naviqore.gtfs.schedule.type.ServiceDayTime;
import org.naviqore.gtfs.schedule.type.TransferType;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.function.Function;

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

    public static final ZoneId ZONE_ID = ZoneId.of("America/New_York");

    private final GtfsScheduleBuilder builder = GtfsSchedule.builder();

    public GtfsRaptorTestSchedule() {
        setup(1, Duration.ZERO, _ -> 1.0);
    }

    /**
     * Creates a customizable GTFS test schedule with multiple trips on Route 2.
     * <p>
     * This constructor allows fine-grained control over the temporal structure of the generated schedule in order to
     * test RAPTOR behavior under varying service patterns. In particular, it supports:
     * <ul>
     *   <li>multiple trips on Route 2,</li>
     *   <li>configurable headway between consecutive trips, and</li>
     *   <li>per-trip speed variations to simulate faster or slower services.</li>
     * </ul>
     * <p>
     * The {@code tripSpeedFactorCalculator} is applied per trip index and scales all stop
     * times of that trip uniformly. Values greater than {@code 1.0} make the trip slower,
     * while values less than {@code 1.0} make it faster.
     *
     * @param numTripsOnRoute2          number of trips to generate on Route 2
     * @param headwayOnRoute2           time offset between consecutive trips on Route 2
     * @param tripSpeedFactorCalculator function mapping a trip index to a speed factor
     */
    public GtfsRaptorTestSchedule(int numTripsOnRoute2, Duration headwayOnRoute2,
                                  Function<Integer, Double> tripSpeedFactorCalculator) {
        setup(numTripsOnRoute2, headwayOnRoute2, tripSpeedFactorCalculator);
    }

    private void setup(int numTripsOnRoute1, Duration headwayOnRoute1,
                       Function<Integer, Double> tripSpeedFactorCalculator) {
        builder.addCalendar("always", EnumSet.allOf(DayOfWeek.class), LocalDate.MIN, LocalDate.MAX);
        builder.addAgency("agency", "Some Agency", "", ZONE_ID);

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
        for (int i = 0; i < numTripsOnRoute1; i++) {
            String tripId = i == 0 ? "T2" : "T%d2".formatted(i);
            int tripOffset = Math.toIntExact(i * headwayOnRoute1.toSeconds());
            double tripSpeed = tripSpeedFactorCalculator.apply(i);
            Function<Integer, ServiceDayTime> serviceDayTimeFactory = seconds -> new ServiceDayTime(
                    (int) (tripOffset + (seconds * tripSpeed)));
            builder.addTrip(tripId, "R2", "always", "D2");
            builder.addStopTime(tripId, "A", serviceDayTimeFactory.apply(60), serviceDayTimeFactory.apply(120));
            builder.addStopTime(tripId, "B2", serviceDayTimeFactory.apply(180), serviceDayTimeFactory.apply(240));
            builder.addStopTime(tripId, "C", serviceDayTimeFactory.apply(300), serviceDayTimeFactory.apply(360));
            builder.addStopTime(tripId, "D2", serviceDayTimeFactory.apply(420), serviceDayTimeFactory.apply(480));
        }
    }

    public void addTransfer(String fromStopId, String toStopId, TransferType type, int duration) {
        builder.addTransfer(fromStopId, toStopId, type, duration);
    }

    public GtfsSchedule build() {
        return builder.build();
    }
}
