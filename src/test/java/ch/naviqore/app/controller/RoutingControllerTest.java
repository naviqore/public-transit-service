package ch.naviqore.app.controller;

import ch.naviqore.app.dto.Connection;
import ch.naviqore.service.PublicTransitService;
import ch.naviqore.service.Stop;
import ch.naviqore.service.exception.StopNotFoundException;
import ch.naviqore.utils.spatial.GeoCoordinate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
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

    @Mock
    private PublicTransitService publicTransitService;

    @InjectMocks
    private RoutingController routingController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetConnections_WithValidSourceAndTargetStopIds() throws StopNotFoundException {
        // Arrange
        String sourceStopId = "sourceStopId";
        String targetStopId = "targetStopId";
        LocalDateTime departureDateTime = LocalDateTime.now();

        Stop sourceStop = mock(Stop.class);
        Stop targetStop = mock(Stop.class);

        try {
            when(publicTransitService.getStopById(sourceStopId)).thenReturn(sourceStop);
            when(publicTransitService.getStopById(targetStopId)).thenReturn(targetStop);
        } catch (StopNotFoundException e) {
            fail("StopNotFoundException was thrown", e);
        }
        when(publicTransitService.getConnections(eq(sourceStop), eq(targetStop), any(), any(), any())).thenReturn(
                Collections.emptyList());

        // Act
        List<Connection> connections = routingController.getConnections(sourceStopId, -1.0, -1.0, targetStopId, -1.0,
                -1.0, departureDateTime, 30, 2, 120, 5);

        // Assert
        assertNotNull(connections);
    }

    @Test
    void testGetConnections_WithoutSourceStopIdButWithCoordinates() {
        // Arrange
        double sourceLatitude = 46.2044;
        double sourceLongitude = 6.1432;
        String targetStopId = "targetStopId";
        LocalDateTime departureDateTime = LocalDateTime.now();

        Stop targetStop = mock(Stop.class);

        try {
            when(publicTransitService.getStopById(targetStopId)).thenReturn(targetStop);
        } catch (StopNotFoundException e) {
            fail("StopNotFoundException was thrown", e);
        }
        when(publicTransitService.getConnections(any(GeoCoordinate.class), eq(targetStop), any(), any(),
                any())).thenReturn(Collections.emptyList());

        // Act
        List<Connection> connections = routingController.getConnections(null, sourceLatitude, sourceLongitude,
                targetStopId, -1.0, -1.0, departureDateTime, 30, 2, 120, 5);

        // Assert
        assertNotNull(connections);
    }

    @Test
    void testGetConnections_MissingSourceAndCoordinates() {
        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            routingController.getConnections(null, -91.0, -181.0, "targetStopId", -91.0, -181.0, LocalDateTime.now(), 30, 2,
                    120, 5);
        });
        assertEquals("Either sourceStopId or sourceLatitude and sourceLongitude must be provided.",
                exception.getReason());
    }

    @Test
    void testGetConnections_MissingTargetAndCoordinates() {
        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            routingController.getConnections("sourceStopId", -91.0, -181.0, null, -91.0, -181.0, LocalDateTime.now(), 30, 2,
                    120, 5);
        });
        assertEquals("Either targetStopId or targetLatitude and targetLongitude must be provided.",
                exception.getReason());
    }

    @Test
    void testGetConnection_InvalidCoordinates() {
        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            routingController.getConnections(null, 91, 181, null, 32, 32, LocalDateTime.now(), 30, 2, 120, 5);
        });
        assertEquals("Coordinates must be valid, Latitude between -90 and 90 and Longitude between -180 and 180.",
                exception.getReason());
    }

    @Test
    void testGetConnections_InvalidMaxTransferNumber() {
        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            routingController.getConnections(null, 0, 0, null, 0, 0, LocalDateTime.now(), 30, -2, 120, 5);
        });
        assertEquals("Max transfer number must be greater than or equal to 0.", exception.getReason());
    }

    @Test
    void testGetConnections_InvalidMaxWalkingDuration() {
        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            routingController.getConnections(null, 0, 0, null, 0, 0, LocalDateTime.now(), -30, 2, 120, 5);
        });
        assertEquals("Max walking duration must be greater than or equal to 0.", exception.getReason());
    }

    @Test
    void testGetConnections_InvalidMaxTravelTime() {
        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            routingController.getConnections(null, 0, 0, null, 0, 0, LocalDateTime.now(), 30, 2, -120, 5);
        });
        assertEquals("Max travel time must be greater than 0.", exception.getReason());
    }

    @Test
    void testGetConnections_InvalidMinTransferTime() {
        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            routingController.getConnections(null, 0, 0, null, 0, 0, LocalDateTime.now(), 30, 2, 120, -5);
        });
        assertEquals("Min transfer time must be greater than or equal to 0.", exception.getReason());
    }

}
