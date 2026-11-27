package org.naviqore.app.controller;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.naviqore.app.config.NaviqoreProperties;
import org.naviqore.app.dto.*;
import org.naviqore.app.exception.InvalidCoordinatesException;
import org.naviqore.app.exception.InvalidParametersException;
import org.naviqore.app.exception.StopNotFoundException;
import org.naviqore.app.exception.ValidationException;
import org.naviqore.utils.spatial.GeoCoordinate;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class RoutingControllerTest {

    private final FakePublicTransitService fakeService = new FakePublicTransitService();

    private final RoutingController routingController = new RoutingController(fakeService, new NaviqoreProperties());

    static Stream<Arguments> provideQueryConfigTestCombinations() {
        int validTimeWindowDuration = 0;
        int validMaxWalkDuration = 30;
        int validMaxTransfers = 2;
        int validMaxTravelDuration = 120;
        int validMinTransferDuration = 5;
        boolean validWheelchairAccessible = false;
        boolean validBikeAllowed = false;
        EnumSet<TravelMode> validTravelModes = EnumSet.allOf(TravelMode.class);

        boolean hasWheelchairAccessible = true;
        boolean hasBikeAllowed = true;
        boolean hasTravelModes = true;

        return Stream.of(
                Arguments.of("validValues", validTimeWindowDuration, validMaxWalkDuration, validMaxTransfers,
                        validMaxTravelDuration, validMinTransferDuration, validWheelchairAccessible, validBikeAllowed,
                        validTravelModes, hasWheelchairAccessible, hasBikeAllowed, hasTravelModes, null),
                Arguments.of("maxWalkDurationEqualsNull", validTimeWindowDuration, null, validMaxTransfers,
                        validMaxTravelDuration, validMinTransferDuration, validWheelchairAccessible, validBikeAllowed,
                        validTravelModes, hasWheelchairAccessible, hasBikeAllowed, hasTravelModes, null),
                Arguments.of("maxTransfersEqualsNull", validTimeWindowDuration, validMaxWalkDuration, null,
                        validMaxTravelDuration, validMinTransferDuration, validWheelchairAccessible, validBikeAllowed,
                        validTravelModes, hasWheelchairAccessible, hasBikeAllowed, hasTravelModes, null),
                Arguments.of("maxTravelDurationEqualsNull", validTimeWindowDuration, validMaxWalkDuration,
                        validMaxTransfers, null, validMinTransferDuration, validWheelchairAccessible, validBikeAllowed,
                        validTravelModes, hasWheelchairAccessible, hasBikeAllowed, hasTravelModes, null),
                Arguments.of("wheelchairAccessibleWhenServiceProvidesNoSupport", validTimeWindowDuration,
                        validMaxWalkDuration, validMaxTransfers, validMaxTravelDuration, validMinTransferDuration, true,
                        validBikeAllowed, validTravelModes, false, hasBikeAllowed, hasTravelModes,
                        "The parameter 'wheelchairAccessible' is not supported by this service."),
                Arguments.of("bikeAllowedWhenServiceProvidesNoSupport", validTimeWindowDuration, validMaxWalkDuration,
                        validMaxTransfers, validMaxTravelDuration, validMinTransferDuration, validWheelchairAccessible,
                        true, validTravelModes, hasWheelchairAccessible, false, hasTravelModes,
                        "The parameter 'bikeAllowed' is not supported by this service."),
                Arguments.of("travelModesWhenServiceProvidesNoSupport", validTimeWindowDuration, validMaxWalkDuration,
                        validMaxTransfers, validMaxTravelDuration, validMinTransferDuration, validWheelchairAccessible,
                        validBikeAllowed, EnumSet.of(TravelMode.BUS), hasWheelchairAccessible, hasBikeAllowed, false,
                        "The parameter 'travelModes' is not supported by this service."),
                Arguments.of("wheelchairAccessibleWhenServiceProvidesSupport", validTimeWindowDuration,
                        validMaxWalkDuration, validMaxTransfers, validMaxTravelDuration, validMinTransferDuration, true,
                        validBikeAllowed, validTravelModes, hasWheelchairAccessible, hasBikeAllowed, hasTravelModes,
                        null),
                Arguments.of("bikeAllowedWhenServiceProvidesSupport", validTimeWindowDuration, validMaxWalkDuration,
                        validMaxTransfers, validMaxTravelDuration, validMinTransferDuration, validWheelchairAccessible,
                        true, validTravelModes, hasWheelchairAccessible, hasBikeAllowed, hasTravelModes, null),
                Arguments.of("travelModesWhenServiceProvidesSupport", validTimeWindowDuration, validMaxWalkDuration,
                        validMaxTransfers, validMaxTravelDuration, validMinTransferDuration, validWheelchairAccessible,
                        validBikeAllowed, EnumSet.of(TravelMode.BUS), hasWheelchairAccessible, hasBikeAllowed,
                        hasTravelModes, null),
                Arguments.of("defaultWheelchairAccessibleWhenNoSupport", validTimeWindowDuration, validMaxWalkDuration,
                        validMaxTransfers, validMaxTravelDuration, validMinTransferDuration, false, validBikeAllowed,
                        validTravelModes, false, hasBikeAllowed, hasTravelModes, null),
                Arguments.of("defaultBikeAllowedWhenNoSupport", validTimeWindowDuration, validMaxWalkDuration,
                        validMaxTransfers, validMaxTravelDuration, validMinTransferDuration, validWheelchairAccessible,
                        false, validTravelModes, hasWheelchairAccessible, false, hasTravelModes, null),
                Arguments.of("defaultTravelModesWhenNoSupport", validTimeWindowDuration, validMaxWalkDuration,
                        validMaxTransfers, validMaxTravelDuration, validMinTransferDuration, validWheelchairAccessible,
                        validBikeAllowed, EnumSet.allOf(TravelMode.class), hasWheelchairAccessible, hasBikeAllowed,
                        false, null),
                Arguments.of("emptyTravelModesWhenNoSupport", validTimeWindowDuration, validMaxWalkDuration,
                        validMaxTransfers, validMaxTravelDuration, validMinTransferDuration, validWheelchairAccessible,
                        validBikeAllowed, EnumSet.noneOf(TravelMode.class), hasWheelchairAccessible, hasBikeAllowed,
                        hasTravelModes, null),
                Arguments.of("nullTravelModesWhenNoSupport", validTimeWindowDuration, validMaxWalkDuration,
                        validMaxTransfers, validMaxTravelDuration, validMinTransferDuration, validWheelchairAccessible,
                        validBikeAllowed, null, hasWheelchairAccessible, hasBikeAllowed, hasTravelModes, null),
                Arguments.of("timeWindowDurationOfTheMaximum",
                        NaviqoreProperties.Routing.DEFAULT_TIME_WINDOW_DURATION_MAXIMUM, validMaxWalkDuration,
                        validMaxTransfers, validMaxTravelDuration, validMinTransferDuration, validWheelchairAccessible,
                        validBikeAllowed, validTravelModes, hasWheelchairAccessible, hasBikeAllowed, hasTravelModes,
                        null),
                Arguments.of("timeWindowDurationLongerThanTheMaximum",
                        NaviqoreProperties.Routing.DEFAULT_TIME_WINDOW_DURATION_MAXIMUM + 1, validMaxWalkDuration,
                        validMaxTransfers, validMaxTravelDuration, validMinTransferDuration, validWheelchairAccessible,
                        validBikeAllowed, validTravelModes, hasWheelchairAccessible, hasBikeAllowed, hasTravelModes,
                        "Time window duration cannot be longer than 86400 seconds."));
    }

    List<Connection> getConnections(String sourceStopId, Double sourceLatitude, Double sourceLongitude,
                                    String targetStopId, Double targetLatitude, Double targetLongitude,
                                    OffsetDateTime departureDateTime) throws org.naviqore.service.exception.ConnectionRoutingException {
        return routingController.getConnections(sourceStopId, sourceLatitude, sourceLongitude, targetStopId,
                targetLatitude, targetLongitude, departureDateTime, TimeType.DEPARTURE, 0, null, null, null, 0, false,
                false, null);
    }

    List<StopConnection> getIsolines(String sourceStopId, Double sourceLatitude, Double sourceLongitude,
                                     OffsetDateTime departureDateTime, TimeType timeType,
                                     boolean returnConnections) throws org.naviqore.service.exception.ConnectionRoutingException {
        return routingController.getIsolines(sourceStopId, sourceLatitude, sourceLongitude, departureDateTime, timeType,
                0, null, null, null, 0, false, false, null, returnConnections);
    }

    @Nested
    class Connections {

        static Stream<Arguments> provideQueryConfigTestCombinations() {
            return RoutingControllerTest.provideQueryConfigTestCombinations();
        }

        @Test
        void testWithValidSourceAndTargetStopIds() throws org.naviqore.service.exception.ConnectionRoutingException {
            String sourceStopId = "A";
            String targetStopId = "G";
            OffsetDateTime departureDateTime = OffsetDateTime.now();

            List<Connection> connections = getConnections(sourceStopId, null, null, targetStopId, null, null,
                    departureDateTime);

            assertNotNull(connections);
        }

        @Test
        void testWithoutSourceStopIdButWithCoordinates() throws org.naviqore.service.exception.ConnectionRoutingException {
            double sourceLatitude = 46.2044;
            double sourceLongitude = 6.1432;
            String targetStopId = "G";
            OffsetDateTime departureDateTime = OffsetDateTime.now();

            List<Connection> connections = getConnections(null, sourceLatitude, sourceLongitude, targetStopId, null,
                    null, departureDateTime);

            assertFalse(connections.isEmpty());
            for (Connection connection : connections) {
                assertEquals(connection.getLegs().getFirst().getDepartureTime(), connection.getDepartureTime());
                assertEquals(connection.getLegs().getLast().getArrivalTime(), connection.getArrivalTime());
            }
        }

        @Test
        void testInvalidStopId() {
            String invalidStopId = "invalidStopId";
            String targetStopId = "G";

            StopNotFoundException exception = assertThrows(StopNotFoundException.class,
                    () -> getConnections(invalidStopId, null, null, targetStopId, null, null, OffsetDateTime.now()));
            assertEquals("The requested source stop with ID 'invalidStopId' was not found.", exception.getMessage());
            assertEquals("invalidStopId", exception.getStopId());
            assertEquals("source", exception.getStopType().orElseThrow().name().toLowerCase(Locale.ROOT));
        }

        @Test
        void testRoutingBetweenSameStops() {
            String sourceStopId = "A";
            String targetStopId = "A";
            OffsetDateTime departureDateTime = OffsetDateTime.now();

            InvalidParametersException exception = assertThrows(InvalidParametersException.class,
                    () -> getConnections(sourceStopId, null, null, targetStopId, null, null, departureDateTime));
            assertEquals("Source and target stop cannot be the same. Please provide different stops.",
                    exception.getMessage());
        }

        @Test
        void testRoutingBetweenSameCoordinates() {
            double latitude = 46.2044;
            double longitude = 6.1432;
            OffsetDateTime departureDateTime = OffsetDateTime.now();

            InvalidParametersException exception = assertThrows(InvalidParametersException.class,
                    () -> getConnections(null, latitude, longitude, null, latitude, longitude, departureDateTime));
            assertEquals("Source and target coordinates cannot be the same. Please provide different coordinates.",
                    exception.getMessage());
        }

        @Test
        void testMissingSourceStopAndSourceCoordinates() {
            InvalidParametersException exception = assertThrows(InvalidParametersException.class,
                    () -> getConnections(null, null, null, "targetStopId", null, null, OffsetDateTime.now()));
            assertEquals("Either sourceStopId or both sourceLatitude and sourceLongitude must be provided.",
                    exception.getMessage());
        }

        @Test
        void testMissingTargetStopAndTargetCoordinates() {
            InvalidParametersException exception = assertThrows(InvalidParametersException.class,
                    () -> getConnections("sourceStopId", null, null, null, null, null, OffsetDateTime.now()));
            assertEquals("Either targetStopId or both targetLatitude and targetLongitude must be provided.",
                    exception.getMessage());
        }

        @Test
        void testGivenSourceStopAndSourceCoordinates() {
            InvalidParametersException exception = assertThrows(InvalidParametersException.class,
                    () -> getConnections("sourceStopId", 0., 0., "targetStopId", null, null, OffsetDateTime.now()));
            assertEquals(
                    "Provide either sourceStopId or coordinates (sourceLatitude and sourceLongitude), but not both.",
                    exception.getMessage());
        }

        @Test
        void testGivenTargetStopAndTargetCoordinates() {
            InvalidParametersException exception = assertThrows(InvalidParametersException.class,
                    () -> getConnections("sourceStopId", null, null, "targetStopId", 0., 0., OffsetDateTime.now()));
            assertEquals(
                    "Provide either targetStopId or coordinates (targetLatitude and targetLongitude), but not both.",
                    exception.getMessage());
        }

        @Test
        void testInvalidCoordinates() {
            InvalidCoordinatesException exception = assertThrows(InvalidCoordinatesException.class,
                    () -> getConnections(null, 91., 181., null, 32., 32., OffsetDateTime.now()));
            assertEquals(
                    "Invalid coordinates. Latitude must be between -90 and 90, longitude must be between -180 and 180.",
                    exception.getMessage());
        }

        @ParameterizedTest(name = "connectionQueryConfig_{0}")
        @MethodSource("provideQueryConfigTestCombinations")
        void testQueryConfigValues(String name, Integer timeWindowDuration, Integer maxWalkDuration,
                                   Integer maxTransfers, Integer maxTravelDuration, int minTransferDuration,
                                   boolean wheelchairAccessible, boolean bikeAllowed, EnumSet<TravelMode> travelModes,
                                   boolean hasWheelchairAccessible, boolean hasBikeAllowed, boolean hasTravelModes,
                                   String errorMessage) throws org.naviqore.service.exception.ConnectionRoutingException {

            fakeService.setHasWheelchairAccessible(hasWheelchairAccessible);
            fakeService.setHasBikeAllowed(hasBikeAllowed);
            fakeService.setHasTravelModes(hasTravelModes);

            if (errorMessage == null) {
                routingController.getConnections(null, 0., 0., null, 1., 1., OffsetDateTime.now(), TimeType.DEPARTURE,
                        timeWindowDuration, maxWalkDuration, maxTransfers, maxTravelDuration, minTransferDuration,
                        wheelchairAccessible, bikeAllowed, travelModes);
            } else {
                ValidationException exception = assertThrows(ValidationException.class,
                        () -> routingController.getConnections(null, 0., 0., null, 1., 1., OffsetDateTime.now(),
                                TimeType.DEPARTURE, timeWindowDuration, maxWalkDuration, maxTransfers,
                                maxTravelDuration, minTransferDuration, wheelchairAccessible, bikeAllowed,
                                travelModes));
                assertEquals(errorMessage, exception.getMessage());
            }
        }
    }

    @Nested
    class Isolines {

        static Stream<Arguments> provideQueryConfigTestCombinations() {
            return RoutingControllerTest.provideQueryConfigTestCombinations();
        }

        @Test
        void testFromStopReturnConnectionsFalse() throws org.naviqore.service.exception.ConnectionRoutingException {
            String sourceStopId = "A";
            OffsetDateTime time = OffsetDateTime.now();

            List<StopConnection> stopConnections = routingController.getIsolines(sourceStopId, null, null, time,
                    TimeType.DEPARTURE, 0, 30, 2, 120, 5, false, false, null, false);

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
        void testFromStopReturnConnectionsTrue() throws org.naviqore.service.exception.ConnectionRoutingException {
            String sourceStopId = "A";
            OffsetDateTime expectedStartTime = OffsetDateTime.now();

            List<StopConnection> stopConnections = routingController.getIsolines(sourceStopId, null, null, null,
                    TimeType.DEPARTURE, 0, 30, 2, 120, 5, false, false, null, true);

            assertNotNull(stopConnections);

            for (StopConnection stopConnection : stopConnections) {
                assertEquals(stopConnection.getStop(), stopConnection.getConnectingLeg().getToStop());

                // because returnConnections == true
                assertNotNull(stopConnection.getConnection());
                assertEquals(stopConnection.getStop(), stopConnection.getConnection().getLegs().getLast().getToStop());
                Connection connection = stopConnection.getConnection();

                // make sure each connection has a departure time after/equal the expected start time
                assertFalse(connection.getLegs().getFirst().getDepartureTime().isBefore(expectedStartTime));
                assertEquals(sourceStopId, connection.getLegs().getFirst().getFromStop().getId());

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
        void testFromCoordinatesReturnConnectionsFalse() throws org.naviqore.service.exception.ConnectionRoutingException {
            double sourceLatitude = 46.2044;
            double sourceLongitude = 6.1432;
            OffsetDateTime time = OffsetDateTime.now();

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
        void testFromCoordinateReturnConnectionsTrue() throws org.naviqore.service.exception.ConnectionRoutingException {
            GeoCoordinate sourceCoordinate = new GeoCoordinate(46.2044, 6.1432);
            OffsetDateTime expectedStartTime = OffsetDateTime.now();

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
        void testFromStopReturnConnectionsTrueTimeTypeArrival() throws org.naviqore.service.exception.ConnectionRoutingException {
            String sourceStopId = "G";

            List<StopConnection> stopConnections = getIsolines(sourceStopId, null, null, null, TimeType.ARRIVAL, true);

            OffsetDateTime expectedArrivalTime = OffsetDateTime.now();
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
                assertEquals(sourceStopId, connection.getLegs().getLast().getToStop().getId());

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
        void testFromCoordinateReturnConnectionsTrueTimeTypeArrival() throws org.naviqore.service.exception.ConnectionRoutingException {
            GeoCoordinate sourceCoordinate = new GeoCoordinate(46.2044, 6.1432);

            List<StopConnection> stopConnections = getIsolines(null, sourceCoordinate.latitude(),
                    sourceCoordinate.longitude(), null, TimeType.ARRIVAL, true);

            OffsetDateTime expectedArrivalTime = OffsetDateTime.now();
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
            String invalidStopId = "invalidStopId";

            StopNotFoundException exception = assertThrows(StopNotFoundException.class,
                    () -> getIsolines(invalidStopId, null, null, OffsetDateTime.now(), TimeType.DEPARTURE, false));

            assertEquals("The requested source stop with ID 'invalidStopId' was not found.", exception.getMessage());
            assertEquals("invalidStopId", exception.getStopId());
            assertEquals("source", exception.getStopType().orElseThrow().name().toLowerCase(Locale.ROOT));
        }

        @Test
        void testMissingSourceStopAndSourceCoordinates() {
            InvalidParametersException exception = assertThrows(InvalidParametersException.class,
                    () -> getIsolines(null, null, null, OffsetDateTime.now(), TimeType.DEPARTURE, false));
            assertEquals("Either sourceStopId or both sourceLatitude and sourceLongitude must be provided.",
                    exception.getMessage());
        }

        @Test
        void testGivenSourceStopAndSourceCoordinates() {
            InvalidParametersException exception = assertThrows(InvalidParametersException.class,
                    () -> getIsolines("sourceStopId", 0., 0.1, OffsetDateTime.now(), TimeType.DEPARTURE, false));
            assertEquals(
                    "Provide either sourceStopId or coordinates (sourceLatitude and sourceLongitude), but not both.",
                    exception.getMessage());
        }

        @Test
        void testInvalidCoordinates() {
            InvalidCoordinatesException exception = assertThrows(InvalidCoordinatesException.class,
                    () -> getIsolines(null, 91., 181., OffsetDateTime.now(), TimeType.DEPARTURE, false));
            assertEquals(
                    "Invalid coordinates. Latitude must be between -90 and 90, longitude must be between -180 and 180.",
                    exception.getMessage());
        }

        @ParameterizedTest(name = "isolineQueryConfig_{0}")
        @MethodSource("provideQueryConfigTestCombinations")
        void testQueryConfigValues(String name, Integer timeWindowDuration, Integer maxWalkDuration,
                                   Integer maxTransfers, Integer maxTravelDuration, int minTransferDuration,
                                   boolean wheelchairAccessible, boolean bikeAllowed, EnumSet<TravelMode> travelModes,
                                   boolean hasWheelchairAccessible, boolean hasBikeAllowed, boolean hasTravelModes,
                                   String errorMessage) throws org.naviqore.service.exception.ConnectionRoutingException {

            fakeService.setHasWheelchairAccessible(hasWheelchairAccessible);
            fakeService.setHasBikeAllowed(hasBikeAllowed);
            fakeService.setHasTravelModes(hasTravelModes);

            if (errorMessage == null) {
                routingController.getIsolines("A", null, null, OffsetDateTime.now(), TimeType.DEPARTURE,
                        timeWindowDuration, maxWalkDuration, maxTransfers, maxTravelDuration, minTransferDuration,
                        wheelchairAccessible, bikeAllowed, travelModes, false);
            } else {
                ValidationException exception = assertThrows(ValidationException.class,
                        () -> routingController.getIsolines("A", null, null, OffsetDateTime.now(), TimeType.DEPARTURE,
                                timeWindowDuration, maxWalkDuration, maxTransfers, maxTravelDuration,
                                minTransferDuration, wheelchairAccessible, bikeAllowed, travelModes, false));
                assertEquals(errorMessage, exception.getMessage());
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

        @ParameterizedTest(name = "maxTransfers_{0}_maxTravelDuration_{1}_maxWalkDuration_{2}_minTransferDuration_{3}_wheelchairAccessible_{4}_bikeAllowed_{5}_travelModes_{6}")
        @MethodSource("provideRoutingInfoTestCombinations")
        void testQueryConfigValues(boolean supportsMaxTransfers, boolean supportsMaxTravelDuration,
                                   boolean supportsMaxWalkDuration, boolean supportsMinTransferDuration,
                                   boolean supportsWheelchairAccessible, boolean supportsBikeAllowed,
                                   boolean supportsTravelModes) {
            fakeService.setHasWheelchairAccessible(supportsWheelchairAccessible);
            fakeService.setHasBikeAllowed(supportsBikeAllowed);
            fakeService.setHasTravelModes(supportsTravelModes);
            fakeService.setSupportsMaximumTransfers(supportsMaxTransfers);
            fakeService.setSupportsMaximumTravelDuration(supportsMaxTravelDuration);
            fakeService.setSupportsMaximumWalkDuration(supportsMaxWalkDuration);
            fakeService.setSupportsMinimumTransferDuration(supportsMinTransferDuration);

            org.naviqore.app.dto.RoutingInfo routingInfo = routingController.getRoutingInfo();

            assertEquals(supportsWheelchairAccessible, routingInfo.isSupportsWheelchairAccessible());
            assertEquals(supportsBikeAllowed, routingInfo.isSupportsBikeAllowed());
            assertEquals(supportsTravelModes, routingInfo.isSupportsTravelModes());
            assertEquals(supportsMaxTransfers, routingInfo.isSupportsMaxTransfers());
            assertEquals(supportsMaxTravelDuration, routingInfo.isSupportsMaxTravelDuration());
            assertEquals(supportsMaxWalkDuration, routingInfo.isSupportsMaxWalkDuration());
            assertEquals(supportsMinTransferDuration, routingInfo.isSupportsMinTransferDuration());
        }
    }
}
