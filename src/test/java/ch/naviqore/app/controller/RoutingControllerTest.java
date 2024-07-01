package ch.naviqore.app.controller;

import ch.naviqore.app.dto.Connection;
import ch.naviqore.app.dto.StopConnection;
import ch.naviqore.app.dto.TimeType;
import ch.naviqore.service.PublicTransitService;
import ch.naviqore.service.Stop;
import ch.naviqore.service.exception.StopNotFoundException;
import ch.naviqore.utils.spatial.GeoCoordinate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RoutingControllerTest {

    private final DummyService dummyService = new DummyService();

    private final RoutingController routingController = new RoutingController(dummyService);

    @Test
    void testGetConnections_WithValidSourceAndTargetStopIds() throws StopNotFoundException {
        // Arrange
        String sourceStopId = "A";
        String targetStopId = "G";
        LocalDateTime departureDateTime = LocalDateTime.now();

        // Act
        List<Connection> connections = routingController.getConnections(sourceStopId, -1.0, -1.0, targetStopId, -1.0,
                -1.0, departureDateTime, TimeType.DEPARTURE, 30, 2, 120, 5);

        // Assert
        assertNotNull(connections);
    }

    @Test
    void testGetConnections_WithoutSourceStopIdButWithCoordinates() throws StopNotFoundException {
        // Arrange
        double sourceLatitude = 46.2044;
        double sourceLongitude = 6.1432;
        String targetStopId = "G";
        LocalDateTime departureDateTime = LocalDateTime.now();

        // Act
        List<Connection> connections = routingController.getConnections(null, sourceLatitude, sourceLongitude,
                targetStopId, -1.0, -1.0, departureDateTime, TimeType.DEPARTURE, 30, 2, 120, 5);

        // Assert
        assertNotNull(connections);
    }

    @Test
    void testGetConnections_InvalidStopId() throws StopNotFoundException {
        // Arrange
        String invalidStopId = "invalidStopId";
        String targetStopId = "G";

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> routingController.getConnections(invalidStopId, -91.0, -181.0, targetStopId, -91.0, -181.0,
                        LocalDateTime.now(), TimeType.DEPARTURE, 30, 2, 120, 5));
        assertEquals("Stop not found", exception.getReason());
        assertEquals(HttpStatusCode.valueOf(404), exception.getStatusCode());
    }

    @Test
    void testGetConnections_MissingSourceAndCoordinates() {
        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> routingController.getConnections(null, -91.0, -181.0, "targetStopId", -91.0, -181.0,
                        LocalDateTime.now(), TimeType.DEPARTURE, 30, 2, 120, 5));
        assertEquals("Either sourceStopId or sourceLatitude and sourceLongitude must be provided.",
                exception.getReason());
        assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
    }

    @Test
    void testGetConnections_MissingTargetAndCoordinates() {
        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> routingController.getConnections("sourceStopId", -91.0, -181.0, null, -91.0, -181.0,
                        LocalDateTime.now(), TimeType.DEPARTURE, 30, 2, 120, 5));
        assertEquals("Either targetStopId or targetLatitude and targetLongitude must be provided.",
                exception.getReason());
        assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
    }

    @Test
    void testGetConnection_InvalidCoordinates() {
        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> routingController.getConnections(null, 91, 181, null, 32, 32, LocalDateTime.now(),
                        TimeType.DEPARTURE, 30, 2, 120, 5));
        assertEquals("Coordinates must be valid, Latitude between -90 and 90 and Longitude between -180 and 180.",
                exception.getReason());
        assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
    }

    @Test
    void testGetConnections_InvalidMaxTransferNumber() {
        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> routingController.getConnections(null, 0, 0, null, 0, 0, LocalDateTime.now(), TimeType.DEPARTURE,
                        30, -2, 120, 5));
        assertEquals("Max transfer number must be greater than or equal to 0.", exception.getReason());
        assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
    }

    @Test
    void testGetConnections_InvalidMaxWalkingDuration() {
        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> routingController.getConnections(null, 0, 0, null, 0, 0, LocalDateTime.now(), TimeType.DEPARTURE,
                        -30, 2, 120, 5));
        assertEquals("Max walking duration must be greater than or equal to 0.", exception.getReason());
        assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
    }

    @Test
    void testGetConnections_InvalidMaxTravelTime() {
        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> routingController.getConnections(null, 0, 0, null, 0, 0, LocalDateTime.now(), TimeType.DEPARTURE,
                        30, 2, -120, 5));
        assertEquals("Max travel time must be greater than 0.", exception.getReason());
        assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
    }

    @Test
    void testGetConnections_InvalidMinTransferTime() {
        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> routingController.getConnections(null, 0, 0, null, 0, 0, LocalDateTime.now(), TimeType.DEPARTURE,
                        30, 2, 120, -5));
        assertEquals("Min transfer time must be greater than or equal to 0.", exception.getReason());
        assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
    }

    @Test
    void testGetIsoLines() throws StopNotFoundException {
        // Arrange
        String sourceStopId = "A";
        LocalDateTime time = LocalDateTime.now();

        // Act
        List<StopConnection> connections = routingController.getIsolines(sourceStopId, -1.0, -1.0, time,
                TimeType.DEPARTURE, 30, 2, 120, 5, false);

        // Assert
        assertNotNull(connections);
    }

    @Test
    void testGetIsoLines_InvalidSourceStopId() throws StopNotFoundException {
        // Arrange
        String invalidStopId = "invalidStopId";

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> routingController.getIsolines(invalidStopId, -91.0, -181.0, LocalDateTime.now(),
                        TimeType.DEPARTURE, 30, 2, 120, 5, false));
        assertEquals("Stop not found", exception.getReason());
        assertEquals(HttpStatusCode.valueOf(404), exception.getStatusCode());
    }

    @Test
    void testGetIsoLines_MissingSourceAndCoordinates() {
        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> routingController.getIsolines(null, -91.0, -181.0, LocalDateTime.now(), TimeType.DEPARTURE, 30, 2,
                        120, 5, false));
        assertEquals("Either sourceStopId or sourceLatitude and sourceLongitude must be provided.",
                exception.getReason());
        assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
    }

    @Test
    void testGetIsoLines_InvalidCoordinates() {
        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> routingController.getIsolines(null, 91, 181, LocalDateTime.now(), TimeType.DEPARTURE, 30, 2, 120,
                        5, false));
        assertEquals("Coordinates must be valid, Latitude between -90 and 90 and Longitude between -180 and 180.",
                exception.getReason());
        assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
    }

    @Test
    void testGetIsoLines_InvalidMaxTransferNumber() {
        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> routingController.getIsolines(null, 0, 0, LocalDateTime.now(), TimeType.DEPARTURE, 30, -2, 120, 5,
                        false));
        assertEquals("Max transfer number must be greater than or equal to 0.", exception.getReason());
        assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
    }

    @Test
    void testGetIsoLines_InvalidMaxWalkingDuration() {
        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> routingController.getIsolines(null, 0, 0, LocalDateTime.now(), TimeType.DEPARTURE, -30, 2, 120, 5,
                        false));
        assertEquals("Max walking duration must be greater than or equal to 0.", exception.getReason());
        assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
    }

    @Test
    void testGetIsoLines_InvalidMaxTravelTime() {
        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> routingController.getIsolines(null, 0, 0, LocalDateTime.now(), TimeType.DEPARTURE, 30, 2, -120, 5,
                        false));
        assertEquals("Max travel time must be greater than 0.", exception.getReason());
        assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
    }

    @Test
    void testGetIsoLines_InvalidMinTransferTime() {
        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> routingController.getIsolines(null, 0, 0, LocalDateTime.now(), TimeType.DEPARTURE, 30, 2, 120, -5,
                        false));
        assertEquals("Min transfer time must be greater than or equal to 0.", exception.getReason());
        assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
    }

}
