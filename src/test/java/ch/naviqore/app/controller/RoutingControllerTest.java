package ch.naviqore.app.controller;

import ch.naviqore.app.dto.*;
import ch.naviqore.service.TravelMode;
import ch.naviqore.utils.spatial.GeoCoordinate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class RoutingControllerTest {

    private final DummyService dummyService = new DummyService();

    private final RoutingController routingController = new RoutingController(dummyService);

    static Stream<Arguments> provideQueryConfigTestCombinations() {
        int validMaxWalkingDuration = 30;
        int validMaxTransferDuration = 2;
        int validMaxTravelTime = 120;
        int validMinTransferTime = 5;
        boolean validWheelChairAccessible = false;
        boolean validBikeAllowed = false;
        EnumSet<TravelMode> validTravelModes = EnumSet.allOf(TravelMode.class);

        boolean hasAccessibilityInformation = true;
        boolean hasBikeInformation = true;
        boolean hasTravelModeInformation = true;

        return Stream.of(
                Arguments.of("validValues", validMaxWalkingDuration, validMaxTransferDuration, validMaxTravelTime,
                        validMinTransferTime, validWheelChairAccessible, validBikeAllowed, validTravelModes,
                        hasAccessibilityInformation, hasBikeInformation, hasTravelModeInformation, null),
                Arguments.of("maxWalkingDurationEqualsNull", null, validMaxTransferDuration, validMaxTravelTime,
                        validMinTransferTime, validWheelChairAccessible, validBikeAllowed, validTravelModes,
                        hasAccessibilityInformation, hasBikeInformation, hasTravelModeInformation, null),
                Arguments.of("invalidMaxWalkingDuration", -1, validMaxTransferDuration, validMaxTravelTime,
                        validMinTransferTime, validWheelChairAccessible, validBikeAllowed, validTravelModes,
                        hasAccessibilityInformation, hasBikeInformation, hasTravelModeInformation,
                        "Max walking duration must be greater than or equal to 0."),
                Arguments.of("maxTransferDurationEqualsNull", validMaxWalkingDuration, null, validMaxTravelTime,
                        validMinTransferTime, validWheelChairAccessible, validBikeAllowed, validTravelModes,
                        hasAccessibilityInformation, hasBikeInformation, hasTravelModeInformation, null),
                Arguments.of("invalidMaxTransferDuration", validMaxWalkingDuration, -1, validMaxTravelTime,
                        validMinTransferTime, validWheelChairAccessible, validBikeAllowed, validTravelModes,
                        hasAccessibilityInformation, hasBikeInformation, hasTravelModeInformation,
                        "Max transfer number must be greater than or equal to 0."),
                Arguments.of("maxTravelTimeEqualsNull", validMaxWalkingDuration, validMaxTransferDuration, null,
                        validMinTransferTime, validWheelChairAccessible, validBikeAllowed, validTravelModes,
                        hasAccessibilityInformation, hasBikeInformation, hasTravelModeInformation, null),
                Arguments.of("invalidMaxTravelTime", validMaxWalkingDuration, validMaxTransferDuration, -1,
                        validMinTransferTime, validWheelChairAccessible, validBikeAllowed, validTravelModes,
                        hasAccessibilityInformation, hasBikeInformation, hasTravelModeInformation,
                        "Max travel time must be greater than 0."),
                Arguments.of("invalidMinTransferTime", validMaxWalkingDuration, validMaxTransferDuration,
                        validMaxTravelTime, -1, validWheelChairAccessible, validBikeAllowed, validTravelModes,
                        hasAccessibilityInformation, hasBikeInformation, hasTravelModeInformation,
                        "Min transfer time must be greater than or equal to 0."),
                Arguments.of("wheelchairAccessibleWhenServiceProvidesNoSupport", validMaxWalkingDuration,
                        validMaxTransferDuration, validMaxTravelTime, validMinTransferTime, true, validBikeAllowed,
                        validTravelModes, false, hasBikeInformation, hasTravelModeInformation,
                        "Accessibility information is not available for this service."),
                Arguments.of("bikeAllowedWhenServiceProvidesNoSupport", validMaxWalkingDuration,
                        validMaxTransferDuration, validMaxTravelTime, validMinTransferTime, validWheelChairAccessible,
                        true, validTravelModes, hasAccessibilityInformation, false, hasTravelModeInformation,
                        "Bike information is not available for this service."),
                Arguments.of("travelModesWhenServiceProvidesNoSupport", validMaxWalkingDuration,
                        validMaxTransferDuration, validMaxTravelTime, validMinTransferTime, validWheelChairAccessible,
                        validBikeAllowed, EnumSet.of(TravelMode.BUS), hasAccessibilityInformation, hasBikeInformation,
                        false, "Service does not support travel mode information."),
                Arguments.of("wheelchairAccessibleWhenServiceProvidesSupport", validMaxWalkingDuration,
                        validMaxTransferDuration, validMaxTravelTime, validMinTransferTime, true, validBikeAllowed,
                        validTravelModes, hasAccessibilityInformation, hasBikeInformation, hasTravelModeInformation,
                        null),
                Arguments.of("bikeAllowedWhenServiceProvidesSupport", validMaxWalkingDuration, validMaxTransferDuration,
                        validMaxTravelTime, validMinTransferTime, validWheelChairAccessible, hasTravelModeInformation,
                        validTravelModes, hasAccessibilityInformation, hasBikeInformation, hasTravelModeInformation,
                        null),
                Arguments.of("travelModesWhenServiceProvidesSupport", validMaxWalkingDuration, validMaxTransferDuration,
                        validMaxTravelTime, validMinTransferTime, validWheelChairAccessible, validBikeAllowed,
                        EnumSet.of(TravelMode.BUS), hasAccessibilityInformation, hasBikeInformation,
                        hasTravelModeInformation, null),
                Arguments.of("defaultWheelchairAccessibleWhenNoSupport", validMaxWalkingDuration,
                        validMaxTransferDuration, validMaxTravelTime, validMinTransferTime, false, validBikeAllowed,
                        validTravelModes, false, hasBikeInformation, hasTravelModeInformation, null),
                Arguments.of("defaultBikeAllowedWhenNoSupport", validMaxWalkingDuration, validMaxTransferDuration,
                        validMaxTravelTime, validMinTransferTime, validWheelChairAccessible, false, validTravelModes,
                        hasAccessibilityInformation, false, hasTravelModeInformation, null),
                Arguments.of("defaultTravelModesWhenNoSupport", validMaxWalkingDuration, validMaxTransferDuration,
                        validMaxTravelTime, validMinTransferTime, validWheelChairAccessible, validBikeAllowed,
                        EnumSet.allOf(TravelMode.class), hasAccessibilityInformation, hasBikeInformation, false, null),
                Arguments.of("emptyTravelModesWhenNoSupport", validMaxWalkingDuration, validMaxTransferDuration,
                        validMaxTravelTime, validMinTransferTime, validWheelChairAccessible, validBikeAllowed,
                        EnumSet.noneOf(TravelMode.class), hasAccessibilityInformation, hasBikeInformation,
                        hasTravelModeInformation, null),
                Arguments.of("nullTravelModesWhenNoSupport", validMaxWalkingDuration, validMaxTransferDuration,
                        validMaxTravelTime, validMinTransferTime, validWheelChairAccessible, validBikeAllowed, null,
                        hasAccessibilityInformation, hasBikeInformation, hasTravelModeInformation, null));
    }

    List<Connection> getConnections(String sourceStopId, Double sourceLatitude, Double sourceLongitude,
                                    String targetStopId, Double targetLatitude, Double targetLongitude,
                                    LocalDateTime departureDateTime) {
        return routingController.getConnections(sourceStopId, sourceLatitude, sourceLongitude, targetStopId,
                targetLatitude, targetLongitude, departureDateTime, TimeType.DEPARTURE, null, null, null, 0, false,
                false, null);
    }

    List<StopConnection> getIsolines(String sourceStopId, Double sourceLatitude, Double sourceLongitude,
                                     LocalDateTime departureDateTime, TimeType timeType, boolean returnConnections) {
        return routingController.getIsolines(sourceStopId, sourceLatitude, sourceLongitude, departureDateTime, timeType,
                null, null, null, 0, false, false, null, returnConnections);
    }

    @Nested
    class Connections {

        static Stream<Arguments> provideQueryConfigTestCombinations() {
            return RoutingControllerTest.provideQueryConfigTestCombinations();
        }

        @Test
        void testWithValidSourceAndTargetStopIds() {
            // Arrange
            String sourceStopId = "A";
            String targetStopId = "G";
            LocalDateTime departureDateTime = LocalDateTime.now();

            // Act
            List<Connection> connections = getConnections(sourceStopId, null, null, targetStopId, null, null,
                    departureDateTime);

            // Assert
            assertNotNull(connections);
        }

        @Test
        void testWithoutSourceStopIdButWithCoordinates() {
            // Arrange
            double sourceLatitude = 46.2044;
            double sourceLongitude = 6.1432;
            String targetStopId = "G";
            LocalDateTime departureDateTime = LocalDateTime.now();

            // Act
            List<Connection> connections = getConnections(null, sourceLatitude, sourceLongitude, targetStopId, null,
                    null, departureDateTime);

            // Assert
            assertNotNull(connections);
        }

        @Test
        void testInvalidStopId() {
            // Arrange
            String invalidStopId = "invalidStopId";
            String targetStopId = "G";

            // Act & Assert
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> getConnections(invalidStopId, null, null, targetStopId, null, null, LocalDateTime.now()));
            assertEquals("The requested source stop with ID 'invalidStopId' was not found.", exception.getReason());
            assertEquals(HttpStatusCode.valueOf(404), exception.getStatusCode());
        }

        @Test
        void testRoutingBetweenSameStops() {
            // Arrange
            String sourceStopId = "A";
            String targetStopId = "A";
            LocalDateTime departureDateTime = LocalDateTime.now();
            // Act & Assert
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> getConnections(sourceStopId, null, null, targetStopId, null, null, departureDateTime));
            assertEquals(
                    "The source stop ID and target stop ID cannot be the same. Please provide different stop IDs for the source and target.",
                    exception.getReason());
            assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
        }

        @Test
        void testMissingSourceStopAndSourceCoordinates() {
            // Act & Assert
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> getConnections(null, null, null, "targetStopId", null, null, LocalDateTime.now()));
            assertEquals("Either sourceStopId or sourceLatitude and sourceLongitude must be provided.",
                    exception.getReason());
            assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
        }

        @Test
        void testMissingTargetStopAndTargetCoordinates() {
            // Act & Assert
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> getConnections("sourceStopId", null, null, null, null, null, LocalDateTime.now()));
            assertEquals("Either targetStopId or targetLatitude and targetLongitude must be provided.",
                    exception.getReason());
            assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
        }

        @Test
        void testGivenSourceStopAndSourceCoordinates() {
            // Act & Assert
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> getConnections("sourceStopId", 0., 0., "targetStopId", null, null, LocalDateTime.now()));
            assertEquals("Only sourceStopId or sourceLatitude and sourceLongitude must be provided, but not both.",
                    exception.getReason());
            assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
        }

        @Test
        void testGivenTargetStopAndTargetCoordinates() {
            // Act & Assert
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> getConnections("sourceStopId", null, null, "targetStopId", 0., 0., LocalDateTime.now()));
            assertEquals("Only targetStopId or targetLatitude and targetLongitude must be provided, but not both.",
                    exception.getReason());
            assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
        }

        @Test
        void testInvalidCoordinates() {
            // Act & Assert
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> getConnections(null, 91., 181., null, 32., 32., LocalDateTime.now()));
            assertEquals("Coordinates must be valid, Latitude between -90 and 90 and Longitude between -180 and 180.",
                    exception.getReason());
            assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
        }

        @ParameterizedTest(name = "connectionQueryConfig_{0}")
        @MethodSource("provideQueryConfigTestCombinations")
        void testQueryConfigValues(String name, Integer maxWalkingDuration, Integer maxTransferDuration,
                                   Integer maxTravelTime, int minTransferTime, boolean wheelChairAccessible,
                                   boolean bikeAllowed, EnumSet<TravelMode> travelModes,
                                   boolean hasAccessibilityInformation, boolean hasBikeInformation,
                                   boolean hasTravelModeInformation, String errorMessage) {

            dummyService.setHasAccessibilityInformation(hasAccessibilityInformation);
            dummyService.setHasBikeInformation(hasBikeInformation);
            dummyService.setHasTravelModeInformation(hasTravelModeInformation);

            if (errorMessage == null) {
                routingController.getConnections(null, 0., 0., null, 0., 0., LocalDateTime.now(), TimeType.DEPARTURE,
                        maxWalkingDuration, maxTransferDuration, maxTravelTime, minTransferTime, wheelChairAccessible,
                        bikeAllowed, travelModes);
            } else {
                ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                        () -> routingController.getConnections(null, 0., 0., null, 0., 0., LocalDateTime.now(),
                                TimeType.DEPARTURE, maxWalkingDuration, maxTransferDuration, maxTravelTime,
                                minTransferTime, wheelChairAccessible, bikeAllowed, travelModes));
                assertEquals(errorMessage, exception.getReason());
                assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
            }
        }
    }

    @Nested
    class Isolines {

        static Stream<Arguments> provideQueryConfigTestCombinations() {
            return RoutingControllerTest.provideQueryConfigTestCombinations();
        }

        @Test
        void testFromStopReturnConnectionsFalse() {
            // Arrange
            String sourceStopId = "A";
            LocalDateTime time = LocalDateTime.now();

            // Act
            List<StopConnection> stopConnections = routingController.getIsolines(sourceStopId, null, null, time,
                    TimeType.DEPARTURE, 30, 2, 120, 5, false, false, null, false);

            assertNotNull(stopConnections);

            for (StopConnection stopConnection : stopConnections) {
                assertEquals(stopConnection.getStop(), stopConnection.getConnectingLeg().getToStop());
                // because returnConnections == false
                assertNull(stopConnection.getConnection());
                Trip trip = stopConnection.getConnectingLeg().getTrip();
                if (trip != null) {
                    assertNull(trip.getStopTimes());
                }
            }
        }

        @Test
        void testFromStopReturnConnectionsTrue() {
            // Arrange
            String sourceStopId = "A";
            // This tests if the time is set to now if null
            LocalDateTime expectedStartTime = LocalDateTime.now();

            List<StopConnection> stopConnections = routingController.getIsolines(sourceStopId, null, null, null,
                    TimeType.DEPARTURE, 30, 2, 120, 5, false, false, null, true);

            assertNotNull(stopConnections);

            for (StopConnection stopConnection : stopConnections) {
                assertEquals(stopConnection.getStop(), stopConnection.getConnectingLeg().getToStop());
                // because returnConnections == true
                assertNotNull(stopConnection.getConnection());
                assertEquals(stopConnection.getStop(), stopConnection.getConnection().getLegs().getLast().getToStop());
                Connection connection = stopConnection.getConnection();
                // make sure each connection has a departure time after/equal the expected start time
                assertFalse(connection.getLegs().getFirst().getDepartureTime().isBefore(expectedStartTime));
                assertEquals(connection.getLegs().getFirst().getFromStop().getId(), sourceStopId);

                Trip trip = stopConnection.getConnectingLeg().getTrip();
                if (trip != null) {
                    List<StopTime> stopTimes = trip.getStopTimes();
                    assertNotNull(stopTimes);
                    // find index of the stopConnection.getStop() in the stopTimes
                    int index = -1;
                    for (int i = 0; i < stopTimes.size(); i++) {
                        if (stopTimes.get(i).getStop().equals(stopConnection.getStop())) {
                            index = i;
                            break;
                        }
                    }
                    if (index == -1) {
                        fail("Stop not found in trip stop times");
                    }
                    // check if the previous stop in the connecting leg is the same as the previous stop in the trip
                    assertEquals(stopTimes.get(index - 1).getStop(), stopConnection.getConnectingLeg().getFromStop());
                }
            }
        }

        @Test
        void testFromCoordinatesReturnConnectionsFalse() {
            // Arrange
            double sourceLatitude = 46.2044;
            double sourceLongitude = 6.1432;
            LocalDateTime time = LocalDateTime.now();

            // Act
            List<StopConnection> stopConnections = getIsolines(null, sourceLatitude, sourceLongitude, time,
                    TimeType.DEPARTURE, false);

            assertNotNull(stopConnections);

            for (StopConnection stopConnection : stopConnections) {
                assertEquals(stopConnection.getStop(), stopConnection.getConnectingLeg().getToStop());
                // because returnConnections == false
                assertNull(stopConnection.getConnection());
                Trip trip = stopConnection.getConnectingLeg().getTrip();
                if (trip != null) {
                    assertNull(trip.getStopTimes());
                }
            }
        }

        @Test
        void testFromCoordinateReturnConnectionsTrue() {
            // Arrange
            GeoCoordinate sourceCoordinate = new GeoCoordinate(46.2044, 6.1432);
            // This tests if the time is set to now if null
            LocalDateTime expectedStartTime = LocalDateTime.now();

            List<StopConnection> stopConnections = getIsolines(null, sourceCoordinate.latitude(),
                    sourceCoordinate.longitude(), null, TimeType.DEPARTURE, true);

            assertNotNull(stopConnections);

            for (StopConnection stopConnection : stopConnections) {
                assertEquals(stopConnection.getStop(), stopConnection.getConnectingLeg().getToStop());
                // because returnConnections == true
                assertNotNull(stopConnection.getConnection());
                assertEquals(stopConnection.getStop(), stopConnection.getConnection().getLegs().getLast().getToStop());
                Connection connection = stopConnection.getConnection();
                // make sure each connection has a departure time after/equal the expected start time
                assertFalse(connection.getLegs().getFirst().getDepartureTime().isBefore(expectedStartTime));
                assertNull(connection.getLegs().getFirst().getFromStop());
                assertEquals(connection.getLegs().getFirst().getFrom(), sourceCoordinate);

                Trip trip = stopConnection.getConnectingLeg().getTrip();
                if (trip != null) {
                    List<StopTime> stopTimes = trip.getStopTimes();
                    assertNotNull(stopTimes);
                    // find index of the stopConnection.getStop() in the stopTimes
                    int index = -1;
                    for (int i = 0; i < stopTimes.size(); i++) {
                        if (stopTimes.get(i).getStop().equals(stopConnection.getStop())) {
                            index = i;
                            break;
                        }
                    }
                    if (index == -1) {
                        fail("Stop not found in trip stop times");
                    }
                    // check if the previous stop in the connecting leg is the same as the previous stop in the trip
                    assertEquals(stopTimes.get(index - 1).getStop(), stopConnection.getConnectingLeg().getFromStop());
                }
            }
        }

        @Test
        void testFromStopReturnConnectionsTrueTimeTypeArrival() {
            // Arrange
            String sourceStopId = "G";

            List<StopConnection> stopConnections = getIsolines(sourceStopId, null, null, null, TimeType.ARRIVAL, true);

            // This tests if the time is set to now if null
            LocalDateTime expectedArrivalTime = LocalDateTime.now();
            assertNotNull(stopConnections);

            for (StopConnection stopConnection : stopConnections) {
                Leg connectingLeg = stopConnection.getConnectingLeg();
                Connection connection = stopConnection.getConnection();

                assertEquals(stopConnection.getStop(), connectingLeg.getFromStop());
                // because returnConnections == true
                assertNotNull(connection);
                assertEquals(stopConnection.getStop(), connection.getLegs().getFirst().getFromStop());

                // make sure each connection has an arrival time after/equal the expected start time
                assertFalse(connection.getLegs().getLast().getArrivalTime().isAfter(expectedArrivalTime));
                assertEquals(connection.getLegs().getLast().getToStop().getId(), sourceStopId);

                Trip trip = connectingLeg.getTrip();
                if (trip != null) {
                    List<StopTime> stopTimes = trip.getStopTimes();
                    assertNotNull(stopTimes);
                    // find index of the stopConnection.getStop() in the stopTimes
                    int index = -1;
                    for (int i = 0; i < stopTimes.size(); i++) {
                        if (stopTimes.get(i).getStop().equals(stopConnection.getStop())) {
                            index = i;
                            break;
                        }
                    }
                    if (index == -1) {
                        fail("Stop not found in trip stop times");
                    }
                    // check if the target stop in the connecting leg is the same as the next stop in the trip
                    assertEquals(stopTimes.get(index + 1).getStop(), connectingLeg.getToStop());
                }
            }
        }

        @Test
        void testFromCoordinateReturnConnectionsTrueTimeTypeArrival() {
            // Arrange
            GeoCoordinate sourceCoordinate = new GeoCoordinate(46.2044, 6.1432);

            List<StopConnection> stopConnections = getIsolines(null, sourceCoordinate.latitude(),
                    sourceCoordinate.longitude(), null, TimeType.ARRIVAL, true);

            // This tests if the time is set to now if null
            LocalDateTime expectedArrivalTime = LocalDateTime.now();
            assertNotNull(stopConnections);

            for (StopConnection stopConnection : stopConnections) {
                Leg connectingLeg = stopConnection.getConnectingLeg();
                Connection connection = stopConnection.getConnection();

                assertEquals(stopConnection.getStop(), connectingLeg.getFromStop());
                // because returnConnections == true
                assertNotNull(connection);
                assertEquals(stopConnection.getStop(), connection.getLegs().getFirst().getFromStop());
                assertEquals(sourceCoordinate, connection.getLegs().getLast().getTo());
                // should be walk transfer from location without stop object
                assertNull(connection.getLegs().getLast().getToStop());

                // make sure each connection has an arrival time before/equal the expected start time
                assertFalse(connection.getLegs().getLast().getArrivalTime().isAfter(expectedArrivalTime));

                Trip trip = connectingLeg.getTrip();
                if (trip != null) {
                    List<StopTime> stopTimes = trip.getStopTimes();
                    assertNotNull(stopTimes);
                    // find index of the stopConnection.getStop() in the stopTimes
                    int index = -1;
                    for (int i = 0; i < stopTimes.size(); i++) {
                        if (stopTimes.get(i).getStop().equals(stopConnection.getStop())) {
                            index = i;
                            break;
                        }
                    }
                    if (index == -1) {
                        fail("Stop not found in trip stop times");
                    }
                    // check if the target stop in the connecting leg is the same as the next stop in the trip
                    assertEquals(stopTimes.get(index + 1).getStop(), connectingLeg.getToStop());
                }
            }
        }

        @Test
        void testInvalidSourceStopId() {
            // Arrange
            String invalidStopId = "invalidStopId";

            // Act & Assert
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> getIsolines(invalidStopId, null, null, LocalDateTime.now(), TimeType.DEPARTURE, false));
            assertEquals("The requested source stop with ID 'invalidStopId' was not found.", exception.getReason());
            assertEquals(HttpStatusCode.valueOf(404), exception.getStatusCode());
        }

        @Test
        void testMissingSourceStopAndSourceCoordinates() {
            // Act & Assert
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> getIsolines(null, null, null, LocalDateTime.now(), TimeType.DEPARTURE, false));
            assertEquals("Either sourceStopId or sourceLatitude and sourceLongitude must be provided.",
                    exception.getReason());
            assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
        }

        @Test
        void testGivenSourceStopAndSourceCoordinates() {
            // Act & Assert
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> getIsolines("sourceStopId", 0., 0.1, LocalDateTime.now(), TimeType.DEPARTURE, false));
            assertEquals("Only sourceStopId or sourceLatitude and sourceLongitude must be provided, but not both.",
                    exception.getReason());
            assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
        }

        @Test
        void testInvalidCoordinates() {
            // Act & Assert
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> getIsolines(null, 91., 181., LocalDateTime.now(), TimeType.DEPARTURE, false));
            assertEquals("Coordinates must be valid, Latitude between -90 and 90 and Longitude between -180 and 180.",
                    exception.getReason());
            assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
        }

        @ParameterizedTest(name = "isolineQueryConfig_{0}")
        @MethodSource("provideQueryConfigTestCombinations")
        void testQueryConfigValues(String name, Integer maxWalkingDuration, Integer maxTransferDuration,
                                   Integer maxTravelTime, int minTransferTime, boolean wheelChairAccessible,
                                   boolean bikeAllowed, EnumSet<TravelMode> travelModes,
                                   boolean hasAccessibilityInformation, boolean hasBikeInformation,
                                   boolean hasTravelModeInformation, String errorMessage) {

            dummyService.setHasAccessibilityInformation(hasAccessibilityInformation);
            dummyService.setHasBikeInformation(hasBikeInformation);
            dummyService.setHasTravelModeInformation(hasTravelModeInformation);

            if (errorMessage == null) {
                routingController.getIsolines("A", null, null, LocalDateTime.now(), TimeType.DEPARTURE,
                        maxWalkingDuration, maxTransferDuration, maxTravelTime, minTransferTime, wheelChairAccessible,
                        bikeAllowed, travelModes, false);
            } else {
                ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                        () -> routingController.getIsolines("A", null, null, LocalDateTime.now(), TimeType.DEPARTURE,
                                maxWalkingDuration, maxTransferDuration, maxTravelTime, minTransferTime,
                                wheelChairAccessible, bikeAllowed, travelModes, false));
                assertEquals(errorMessage, exception.getReason());
                assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
            }
        }
    }

    @Nested
    class RoutingInfo {
        static Stream<Arguments> provideRoutingInfoTestCombinations() {
            List<boolean[]> combinations = generateBooleanCombinations(7);
            return combinations.stream()
                    .map(arr -> Arguments.of(arr[0], arr[1], arr[2], arr[3], arr[4], arr[5], arr[6]));
        }

        private static List<boolean[]> generateBooleanCombinations(int length) {
            return Stream.iterate(new boolean[length], arr -> {
                boolean[] next = Arrays.copyOf(arr, length);
                for (int i = length - 1; i >= 0; i--) {
                    if (next[i]) {
                        next[i] = false;
                    } else {
                        next[i] = true;
                        break;
                    }
                }
                return next;
            }).limit((long) Math.pow(2, length)).collect(Collectors.toList());
        }

        @ParameterizedTest(name = "maxNumTransfers_{0}_maxTravelTime_{1}_maxWalkingTime_{2}_minTransferTime_{3}_accessibility_{4}_bikes_{5}_travelModes_{6}")
        @MethodSource("provideRoutingInfoTestCombinations")
        void testQueryConfigValues(boolean supportsMaxNumTransfers, boolean supportsMaxTravelTime,
                                   boolean supportsMaxWalkingDuration, boolean supportsMinTransferDuration,
                                   boolean supportsAccessibility, boolean supportsBikes, boolean supportsTravelModes) {
            dummyService.setHasAccessibilityInformation(supportsAccessibility);
            dummyService.setHasBikeInformation(supportsBikes);
            dummyService.setHasTravelModeInformation(supportsTravelModes);
            dummyService.setSupportsMaxTransferNumber(supportsMaxNumTransfers);
            dummyService.setSupportsMaxTravelTime(supportsMaxTravelTime);
            dummyService.setSupportsMaxWalkingDuration(supportsMaxWalkingDuration);
            dummyService.setSupportsMinTransferDuration(supportsMinTransferDuration);

            ch.naviqore.app.dto.RoutingInfo routingInfo = routingController.getRoutingInfo();

            assertEquals(supportsAccessibility, routingInfo.isSupportsAccessibility());
            assertEquals(supportsBikes, routingInfo.isSupportsBikes());
            assertEquals(supportsTravelModes, routingInfo.isSupportsTravelModes());
            assertEquals(supportsMaxNumTransfers, routingInfo.isSupportsMaxNumTransfers());
            assertEquals(supportsMaxTravelTime, routingInfo.isSupportsMaxTravelTime());
            assertEquals(supportsMaxWalkingDuration, routingInfo.isSupportsMaxWalkingDuration());
            assertEquals(supportsMinTransferDuration, routingInfo.isSupportsMinTransferDuration());
        }
    }
}
