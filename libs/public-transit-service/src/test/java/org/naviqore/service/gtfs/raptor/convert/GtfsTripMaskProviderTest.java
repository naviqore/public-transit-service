package org.naviqore.service.gtfs.raptor.convert;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.naviqore.gtfs.schedule.model.GtfsSchedule;
import org.naviqore.gtfs.schedule.model.GtfsScheduleBuilder;
import org.naviqore.gtfs.schedule.type.*;
import org.naviqore.raptor.QueryConfig;
import org.naviqore.raptor.TravelMode;
import org.naviqore.raptor.router.RaptorTripMaskProvider;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GtfsTripMaskProviderTest {

    private static final LocalDate WEEKDAY = LocalDate.of(2020, 1, 2);
    private static final LocalDate WEEKEND = LocalDate.of(2020, 1, 4);
    private static final LocalDate EXCEPTION = LocalDate.of(2020, 1, 1);

    static Stream<Arguments> provideTestCases() {
        List<Arguments> argumentsList = new ArrayList<>();
        argumentsList.add(
                Arguments.of("Weekday, all travel modes, all accessibility, all bike", WEEKDAY, new QueryConfig()));
        argumentsList.add(
                Arguments.of("Weekend, all travel modes, all accessibility, all bike", WEEKEND, new QueryConfig()));
        argumentsList.add(
                Arguments.of("Exception, all travel modes, all accessibility, all bike", EXCEPTION, new QueryConfig()));

        QueryConfig queryConfig = new QueryConfig();
        queryConfig.setAllowedTravelModes(EnumSet.of(TravelMode.BUS));
        argumentsList.add(Arguments.of("Weekday, bus only, all accessibility, all bike", WEEKDAY, queryConfig));

        queryConfig = new QueryConfig();
        queryConfig.setAllowedTravelModes(EnumSet.of(TravelMode.BUS, TravelMode.TRAM));
        argumentsList.add(Arguments.of("Weekday, bus and tram, all accessibility, all bike", WEEKDAY, queryConfig));

        queryConfig = new QueryConfig();
        queryConfig.setWheelchairAccessible(true);
        argumentsList.add(Arguments.of("Weekday, all travel modes, accessible only, all bike", WEEKDAY, queryConfig));

        queryConfig = new QueryConfig();
        queryConfig.setBikeAccessible(true);
        argumentsList.add(
                Arguments.of("Weekday, all travel modes, all accessibility, bike allowed", WEEKDAY, queryConfig));

        queryConfig = new QueryConfig();
        queryConfig.setAllowedTravelModes(EnumSet.of(TravelMode.BUS));
        queryConfig.setWheelchairAccessible(true);
        queryConfig.setBikeAccessible(true);
        argumentsList.add(Arguments.of("Weekday, bus only, accessible only, bike allowed", WEEKDAY, queryConfig));

        return argumentsList.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideTestCases")
    void testMasks(String name, LocalDate date, QueryConfig queryConfig) {
        GtfsTripMaskProvider provider = new GtfsTripMaskProvider(Utilities.prepareSchedule());
        provider.setTripIds(Utilities.getTripIds());

        List<String> expectedRoutes = queryConfig.getAllowedTravelModes()
                .stream()
                .map(Utilities.TRAVEL_MODE_MAP::get)
                .filter(Objects::nonNull)
                .toList();

        String[] expectedServiceIds = date.equals(WEEKDAY) ? new String[]{"weekdays"} : date.equals(
                WEEKEND) ? new String[]{"weekends"} : new String[]{"exception"};

        AccessibilityInformation[] expectedAccessibilityValues = queryConfig.isWheelchairAccessible() ? new AccessibilityInformation[]{
                AccessibilityInformation.ACCESSIBLE} : Utilities.ACCESSIBILITY_VALUES;

        BikeInformation[] expectedBikeValues = queryConfig.isBikeAccessible() ? new BikeInformation[]{
                BikeInformation.ALLOWED} : Utilities.BIKE_VALUES;

        boolean[] expectedTripMask = Utilities.getExpectedTripMask(expectedServiceIds, expectedAccessibilityValues,
                expectedBikeValues);

        Map<String, RaptorTripMaskProvider.RouteTripMask> tripMasks = provider.getDayTripMask(date, queryConfig)
                .tripMask();

        // check that all routes are present
        for (String routeId : Utilities.ROUTE_IDS) {
            assertTrue(tripMasks.containsKey(routeId), "Route " + routeId + " is missing in the trip mask");
        }

        for (Map.Entry<String, RaptorTripMaskProvider.RouteTripMask> entry : tripMasks.entrySet()) {
            String routeId = entry.getKey();
            RaptorTripMaskProvider.RouteTripMask routeTripMask = entry.getValue();
            boolean[] tripMask = routeTripMask.routeTripMask();

            if (expectedRoutes.contains(routeId)) {
                assertArrayEquals(expectedTripMask, tripMask, "Trip mask for route " + routeId + " does not match");
            } else {
                assertArrayEquals(new boolean[Utilities.NUM_TRIPS_PER_ROUTE], tripMask,
                        "Trip mask for route " + routeId + " should have no active trips");
            }
        }
    }

    static class Utilities {

        private static final String[] ROUTE_IDS = {"route1", "route2", "route3"};
        private static final String[] SERVICE_IDS = {"weekdays", "weekends", "exception"};
        private static final AccessibilityInformation[] ACCESSIBILITY_VALUES = {AccessibilityInformation.UNKNOWN,
                AccessibilityInformation.ACCESSIBLE, AccessibilityInformation.NOT_ACCESSIBLE};
        private static final BikeInformation[] BIKE_VALUES = {BikeInformation.UNKNOWN, BikeInformation.ALLOWED,
                BikeInformation.NOT_ALLOWED};
        private static final int NUM_TRIPS_PER_ROUTE = SERVICE_IDS.length * ACCESSIBILITY_VALUES.length * BIKE_VALUES.length;
        private static final Map<TravelMode, String> TRAVEL_MODE_MAP = Map.of(TravelMode.BUS, "route1", TravelMode.TRAM,
                "route2", TravelMode.RAIL, "route3");

        private static GtfsSchedule prepareSchedule() {
            GtfsScheduleBuilder builder = GtfsSchedule.builder();

            builder.addAgency("agency1", "Agency 1", "abc", ZoneId.of("Europe/Zurich"));
            builder.addStop("stop1", "Stop 1", 47.0, 8.0);
            builder.addStop("stop2", "Stop 2", 47.1, 8.1);

            builder.addRoute("route1", "agency1", "Route 1", "Route 1 - Bus", HierarchicalVehicleType.BUS_SERVICE);
            builder.addRoute("route2", "agency1", "Route 2", "Route 2 - Tram", HierarchicalVehicleType.TRAM_SERVICE);
            builder.addRoute("route3", "agency1", "Route 3", "Route 3 - Rail", HierarchicalVehicleType.RAILWAY_SERVICE);

            builder.addCalendar("weekdays", EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), LocalDate.MIN,
                    LocalDate.MAX);
            builder.addCalendar("weekends", EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), LocalDate.MIN,
                    LocalDate.MAX);
            // January 1st, 2020 is a Wednesday (weekday)
            builder.addCalendarDate("exception", LocalDate.of(2020, 1, 1), ExceptionType.ADDED);
            builder.addCalendarDate("weekdays", LocalDate.of(2020, 1, 1), ExceptionType.REMOVED);

            for (String routeId : ROUTE_IDS) {
                for (int tripCounter = 0; tripCounter < NUM_TRIPS_PER_ROUTE; tripCounter++) {
                    String serviceId = getServiceIdForTripIndex(tripCounter);
                    AccessibilityInformation accessibilityValue = getAccessibilityInformation(tripCounter);
                    BikeInformation bikeValue = getBikeInformation(tripCounter);
                    String tripId = getTripId(routeId, tripCounter);

                    builder.addTrip(tripId, routeId, serviceId, tripId, accessibilityValue, bikeValue);
                    builder.addStopTime(tripId, "stop1", new ServiceDayTime(0), new ServiceDayTime(0));
                    builder.addStopTime(tripId, "stop2", new ServiceDayTime(60), new ServiceDayTime(60));
                }
            }
            return builder.build();
        }

        private static boolean[] getExpectedTripMask(String[] serviceIds,
                                                     AccessibilityInformation[] accessibilityValues,
                                                     BikeInformation[] bikeValues) {
            boolean[] expectedTripMask = new boolean[NUM_TRIPS_PER_ROUTE];
            for (int tripCounter = 0; tripCounter < NUM_TRIPS_PER_ROUTE; tripCounter++) {
                if (!Arrays.asList(serviceIds).contains(getServiceIdForTripIndex(tripCounter))) {
                    continue;
                }
                if (!Arrays.asList(accessibilityValues).contains(getAccessibilityInformation(tripCounter))) {
                    continue;
                }
                if (!Arrays.asList(bikeValues).contains(getBikeInformation(tripCounter))) {
                    continue;
                }
                expectedTripMask[tripCounter] = true;
            }
            return expectedTripMask;
        }

        private static Map<String, String[]> getTripIds() {
            Map<String, String[]> tripMap = new HashMap<>();
            for (String routeId : ROUTE_IDS) {
                String[] tripIds = new String[NUM_TRIPS_PER_ROUTE];
                for (int tripCounter = 0; tripCounter < NUM_TRIPS_PER_ROUTE; tripCounter++) {
                    tripIds[tripCounter] = getTripId(routeId, tripCounter);
                }
                tripMap.put(routeId, tripIds);
            }
            return tripMap;
        }

        private static String getServiceIdForTripIndex(int tripIndex) {
            return SERVICE_IDS[tripIndex % SERVICE_IDS.length];
        }

        private static AccessibilityInformation getAccessibilityInformation(int tripIndex) {
            return ACCESSIBILITY_VALUES[(tripIndex / SERVICE_IDS.length) % ACCESSIBILITY_VALUES.length];
        }

        private static BikeInformation getBikeInformation(int tripIndex) {
            return BIKE_VALUES[(tripIndex / (SERVICE_IDS.length * ACCESSIBILITY_VALUES.length)) % BIKE_VALUES.length];
        }

        private static String getTripId(String routeId, int tripIndex) {
            return routeId + "-trip" + tripIndex;
        }

    }

}