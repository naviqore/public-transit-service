package ch.naviqore.app.controller;

import ch.naviqore.app.dto.*;
import ch.naviqore.service.TimeType;
import ch.naviqore.utils.spatial.GeoCoordinate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RoutingControllerTest {

    private final DummyService dummyService = new DummyService();

    private final RoutingController routingController = new RoutingController(dummyService);

    @Nested
    class Connections {

        @Test
        void testWithValidSourceAndTargetStopIds() {
            // Arrange
            String sourceStopId = "A";
            String targetStopId = "G";
            LocalDateTime departureDateTime = LocalDateTime.now();

            // Act
            List<Connection> connections = routingController.getConnections(sourceStopId, null, null, targetStopId,
                    null, null, departureDateTime, TimeType.DEPARTURE, 30, 2, 120, 5);

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
            List<Connection> connections = routingController.getConnections(null, sourceLatitude, sourceLongitude,
                    targetStopId, null, null, departureDateTime, TimeType.DEPARTURE, 30, 2, 120, 5);

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
                    () -> routingController.getConnections(invalidStopId, null, null, targetStopId, null, null,
                            LocalDateTime.now(), TimeType.DEPARTURE, 30, 2, 120, 5));
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
                    () -> routingController.getConnections(sourceStopId, null, null, targetStopId, null, null,
                            departureDateTime, TimeType.DEPARTURE, 30, 2, 120, 5));
            assertEquals(
                    "The source stop ID and target stop ID cannot be the same. Please provide different stop IDs for the source and target.",
                    exception.getReason());
            assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
        }

        @Test
        void testMissingSourceStopAndSourceCoordinates() {
            // Act & Assert
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> routingController.getConnections(null, null, null, "targetStopId", null, null,
                            LocalDateTime.now(), TimeType.DEPARTURE, 30, 2, 120, 5));
            assertEquals("Either sourceStopId or sourceLatitude and sourceLongitude must be provided.",
                    exception.getReason());
            assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
        }

        @Test
        void testMissingTargetStopAndTargetCoordinates() {
            // Act & Assert
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> routingController.getConnections("sourceStopId", null, null, null, null, null,
                            LocalDateTime.now(), TimeType.DEPARTURE, 30, 2, 120, 5));
            assertEquals("Either targetStopId or targetLatitude and targetLongitude must be provided.",
                    exception.getReason());
            assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
        }

        @Test
        void testGivenSourceStopAndSourceCoordinates() {
            // Act & Assert
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> routingController.getConnections("sourceStopId", 0., 0., "targetStopId", null, null,
                            LocalDateTime.now(), TimeType.DEPARTURE, 30, 2, 120, 5));
            assertEquals("Only sourceStopId or sourceLatitude and sourceLongitude must be provided, but not both.",
                    exception.getReason());
            assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
        }

        @Test
        void testGivenTargetStopAndTargetCoordinates() {
            // Act & Assert
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> routingController.getConnections("sourceStopId", null, null, "targetStopId", 0., 0.,
                            LocalDateTime.now(), TimeType.DEPARTURE, 30, 2, 120, 5));
            assertEquals("Only targetStopId or targetLatitude and targetLongitude must be provided, but not both.",
                    exception.getReason());
            assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
        }

        @Test
        void testInvalidCoordinates() {
            // Act & Assert
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> routingController.getConnections(null, 91., 181., null, 32., 32., LocalDateTime.now(),
                            TimeType.DEPARTURE, 30, 2, 120, 5));
            assertEquals("Coordinates must be valid, Latitude between -90 and 90 and Longitude between -180 and 180.",
                    exception.getReason());
            assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
        }

        @Test
        void testInvalidMaxTransferNumber() {
            // Act & Assert
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> routingController.getConnections(null, 0., 0., null, 0., 0., LocalDateTime.now(),
                            TimeType.DEPARTURE, 30, -2, 120, 5));
            assertEquals("Max transfer number must be greater than or equal to 0.", exception.getReason());
            assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
        }

        @Test
        void testInvalidMaxWalkingDuration() {
            // Act & Assert
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> routingController.getConnections(null, 0., 0., null, 0., 0., LocalDateTime.now(),
                            TimeType.DEPARTURE, -30, 2, 120, 5));
            assertEquals("Max walking duration must be greater than or equal to 0.", exception.getReason());
            assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
        }

        @Test
        void testInvalidMaxTravelTime() {
            // Act & Assert
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> routingController.getConnections(null, 0., 0., null, 0., 0., LocalDateTime.now(),
                            TimeType.DEPARTURE, 30, 2, -120, 5));
            assertEquals("Max travel time must be greater than 0.", exception.getReason());
            assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
        }

        @Test
        void testInvalidMinTransferTime() {
            // Act & Assert
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> routingController.getConnections(null, 0., 0., null, 0., 0., LocalDateTime.now(),
                            TimeType.DEPARTURE, 30, 2, 120, -5));
            assertEquals("Min transfer time must be greater than or equal to 0.", exception.getReason());
            assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
        }
    }

    @Nested
    class Isolines {

        @Test
        void testFromStopReturnConnectionsFalse() {
            // Arrange
            String sourceStopId = "A";
            LocalDateTime time = LocalDateTime.now();

            // Act
            List<StopConnection> stopConnections = routingController.getIsolines(sourceStopId, null, null, time,
                    TimeType.DEPARTURE, 30, 2, 120, 5, false);

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
                    TimeType.DEPARTURE, 30, 2, 120, 5, true);

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
            List<StopConnection> stopConnections = routingController.getIsolines(null, sourceLatitude, sourceLongitude,
                    time, TimeType.DEPARTURE, 30, 2, 120, 5, false);

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

            List<StopConnection> stopConnections = routingController.getIsolines(null, sourceCoordinate.latitude(),
                    sourceCoordinate.longitude(), null, TimeType.DEPARTURE, 30, 2, 120, 5, true);

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

            List<StopConnection> stopConnections = routingController.getIsolines(sourceStopId, null, null, null,
                    TimeType.ARRIVAL, 30, 2, 120, 5, true);

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

            List<StopConnection> stopConnections = routingController.getIsolines(null, sourceCoordinate.latitude(),
                    sourceCoordinate.longitude(), null, TimeType.ARRIVAL, 30, 2, 120, 5, true);

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
                    () -> routingController.getIsolines(invalidStopId, null, null, LocalDateTime.now(),
                            TimeType.DEPARTURE, 30, 2, 120, 5, false));
            assertEquals("The requested source stop with ID 'invalidStopId' was not found.", exception.getReason());
            assertEquals(HttpStatusCode.valueOf(404), exception.getStatusCode());
        }

        @Test
        void testMissingSourceStopAndSourceCoordinates() {
            // Act & Assert
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> routingController.getIsolines(null, null, null, LocalDateTime.now(), TimeType.DEPARTURE, 30,
                            2, 120, 5, false));
            assertEquals("Either sourceStopId or sourceLatitude and sourceLongitude must be provided.",
                    exception.getReason());
            assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
        }

        @Test
        void testGivenSourceStopAndSourceCoordinates() {
            // Act & Assert
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> routingController.getIsolines("sourceStopId", 0., 0.1, LocalDateTime.now(),
                            TimeType.DEPARTURE, 30, 2, 120, 5, false));
            assertEquals("Only sourceStopId or sourceLatitude and sourceLongitude must be provided, but not both.",
                    exception.getReason());
            assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
        }

        @Test
        void testInvalidCoordinates() {
            // Act & Assert
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> routingController.getIsolines(null, 91., 181., LocalDateTime.now(), TimeType.DEPARTURE, 30, 2,
                            120, 5, false));
            assertEquals("Coordinates must be valid, Latitude between -90 and 90 and Longitude between -180 and 180.",
                    exception.getReason());
            assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
        }

        @Test
        void testInvalidMaxTransferNumber() {
            // Act & Assert
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> routingController.getIsolines(null, 0., 0., LocalDateTime.now(), TimeType.DEPARTURE, 30, -2,
                            120, 5, false));
            assertEquals("Max transfer number must be greater than or equal to 0.", exception.getReason());
            assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
        }

        @Test
        void testInvalidMaxWalkingDuration() {
            // Act & Assert
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> routingController.getIsolines(null, 0., 0., LocalDateTime.now(), TimeType.DEPARTURE, -30, 2,
                            120, 5, false));
            assertEquals("Max walking duration must be greater than or equal to 0.", exception.getReason());
            assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
        }

        @Test
        void testInvalidMaxTravelTime() {
            // Act & Assert
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> routingController.getIsolines(null, 0., 0., LocalDateTime.now(), TimeType.DEPARTURE, 30, 2,
                            -120, 5, false));
            assertEquals("Max travel time must be greater than 0.", exception.getReason());
            assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
        }

        @Test
        void testInvalidMinTransferTime() {
            // Act & Assert
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> routingController.getIsolines(null, 0., 0., LocalDateTime.now(), TimeType.DEPARTURE, 30, 2,
                            120, -5, false));
            assertEquals("Min transfer time must be greater than or equal to 0.", exception.getReason());
            assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
        }

    }
}
