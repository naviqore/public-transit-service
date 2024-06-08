package ch.naviqore.app.controller;

import ch.naviqore.app.dto.Departure;
import ch.naviqore.app.dto.DistanceToStop;
import ch.naviqore.app.dto.SearchType;
import ch.naviqore.app.dto.Stop;
import ch.naviqore.service.ScheduleInformationService;
import ch.naviqore.service.exception.StopNotFoundException;
import ch.naviqore.utils.spatial.GeoCoordinate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static ch.naviqore.app.dto.DtoMapper.map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ScheduleControllerTest {

    @Mock
    private ScheduleInformationService scheduleInformationService;

    @InjectMocks
    private ScheduleController scheduleController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetAutoCompleteStops() {
        String query = "query";
        when(scheduleInformationService.getStops(query, map(SearchType.STARTS_WITH))).thenReturn(List.of());
        List<Stop> stops = scheduleController.getAutoCompleteStops(query, 10, SearchType.STARTS_WITH);

        assertNotNull(stops);
    }

    @Test
    void testGetAutoCompleteStops_withNegativeLimit() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            scheduleController.getAutoCompleteStops("query", -10, SearchType.STARTS_WITH);
        });

        assertEquals("Limit must be greater than 0", exception.getReason());
        assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
    }

    @Test
    void testGetAutoCompleteStops_withZeroLimit() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            scheduleController.getAutoCompleteStops("query", 0, SearchType.STARTS_WITH);
        });

        assertEquals("Limit must be greater than 0", exception.getReason());
        assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
    }

    @Test
    void testGetNearestStops() {
        GeoCoordinate location = new GeoCoordinate(0, 0);
        int radius = 1000;
        int limit = 10;

        when(scheduleInformationService.getNearestStops(location, radius, limit)).thenReturn(List.of());

        List<DistanceToStop> stops = scheduleController.getNearestStops(location.latitude(), location.longitude(),
                radius, limit);

        assertNotNull(stops);
    }

    @Test
    void testGetNearestStops_withNegativeLimit() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            scheduleController.getNearestStops(0, 0, 1000, -10);
        });

        assertEquals("Limit must be greater than 0", exception.getReason());
        assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
    }

    @Test
    void testGetNearestStops_withZeroLimit() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            scheduleController.getNearestStops(0, 0, 1000, 0);
        });

        assertEquals("Limit must be greater than 0", exception.getReason());
        assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
    }

    @Test
    void testGetNearestStops_withNegativeMaxDistance() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            scheduleController.getNearestStops(0, 0, -1000, 10);
        });

        assertEquals("Max distance can not be negative", exception.getReason());
        assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
    }

    @ParameterizedTest(name = "Test case {index}: Latitude={0}, Longitude={1}")
    @CsvSource({
            "91, 0",    // Invalid latitude
            "-91, 0",   // Invalid latitude
            "0, 181",   // Invalid longitude
            "0, -181",  // Invalid longitude
            "91, 181",  // Invalid latitude and longitude
            "-91, -181" // Invalid latitude and longitude
    })
    void testGetNearestStops_withInvalidCoordinates(double latitude, double longitude) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            scheduleController.getNearestStops(latitude, longitude, 1000, 10);
        });
        assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
    }

    @Test
    void testGetStop() throws StopNotFoundException {
        String stopId = "stopId";
        ch.naviqore.service.Stop serviceStop = mock(ch.naviqore.service.Stop.class);
        when(scheduleInformationService.getStopById(stopId)).thenReturn(serviceStop);
        Stop stop = scheduleController.getStop(stopId);
        assertNotNull(stop);
    }

    @Test
    void testGetStop_withStopNotFoundException() throws StopNotFoundException {
        String stopId = "stopId";
        when(scheduleInformationService.getStopById(stopId)).thenThrow(StopNotFoundException.class);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            scheduleController.getStop(stopId);
        });
        assertEquals(HttpStatusCode.valueOf(404), exception.getStatusCode());
    }

    @Test
    void testGetDepartures() throws StopNotFoundException {
        String stopId = "stopId";
        ch.naviqore.service.Stop serviceStop = mock(ch.naviqore.service.Stop.class);
        when(scheduleInformationService.getStopById(stopId)).thenReturn(serviceStop);
        List<ch.naviqore.service.StopTime> stopTimes = List.of();
        when(scheduleInformationService.getNextDepartures(serviceStop, null, null, 10)).thenReturn(stopTimes);
        List<Departure> stopTimeDtos = scheduleController.getDepartures(stopId, null, 10, null);
        assertNotNull(stopTimeDtos);
    }

    @Test
    void testGetDepartures_withStopNotFoundException() throws StopNotFoundException {
        String stopId = "stopId";
        when(scheduleInformationService.getStopById(stopId)).thenThrow(StopNotFoundException.class);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            scheduleController.getDepartures(stopId, null, 10, null);
        });
        assertEquals(HttpStatusCode.valueOf(404), exception.getStatusCode());
    }

    @Test
    void testGetDepartures_withUntilDateTimeBeforeDepartureDateTime() throws StopNotFoundException {
        String stopId = "stopId";
        LocalDateTime departureTime = LocalDateTime.now();
        LocalDateTime untilTime = departureTime.minusMinutes(1);

        when(scheduleInformationService.getStopById(stopId)).thenReturn(mock(ch.naviqore.service.Stop.class));
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            scheduleController.getDepartures(stopId, departureTime, 10, untilTime);
        });
        assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
    }

    @Test
    void testGetDepartures_withNullDepartureDateTime() throws StopNotFoundException {
        String stopId = "stopId";
        int limit = 10;
        LocalDateTime untilTime = LocalDateTime.now().plusMinutes(1);
        ch.naviqore.service.Stop serviceStop = mock(ch.naviqore.service.Stop.class);
        when(scheduleInformationService.getStopById(stopId)).thenReturn(serviceStop);
        List<ch.naviqore.service.StopTime> stopTimes = List.of();
        when(scheduleInformationService.getNextDepartures(eq(serviceStop), any(), eq(untilTime)
                , eq(limit))).thenReturn(stopTimes);
        List<Departure> stopTimeDtos = scheduleController.getDepartures(stopId, null, limit, untilTime);
        assertNotNull(stopTimeDtos);
    }

    @Test
    void testGetDepartures_withDepartureDateTime() throws StopNotFoundException {
        String stopId = "stopId";
        int limit = 10;
        LocalDateTime departureTime = LocalDateTime.now().plusDays(2);
        LocalDateTime untilTime = departureTime.plusMinutes(1);
        ch.naviqore.service.Stop serviceStop = mock(ch.naviqore.service.Stop.class);
        when(scheduleInformationService.getStopById(stopId)).thenReturn(serviceStop);
        List<ch.naviqore.service.StopTime> stopTimes = List.of();
        when(scheduleInformationService.getNextDepartures(eq(serviceStop), eq(departureTime), eq(untilTime)
                , eq(limit))).thenReturn(stopTimes);
        List<Departure> stopTimeDtos = scheduleController.getDepartures(stopId, departureTime, limit, untilTime);
        assertNotNull(stopTimeDtos);
    }

    @Test
    void testGetDepartures_withNullUntilDateTime() throws StopNotFoundException {
        String stopId = "stopId";
        int limit = 10;
        LocalDateTime departureTime = LocalDateTime.now().plusDays(2);
        ch.naviqore.service.Stop serviceStop = mock(ch.naviqore.service.Stop.class);
        when(scheduleInformationService.getStopById(stopId)).thenReturn(serviceStop);
        List<ch.naviqore.service.StopTime> stopTimes = List.of();
        when(scheduleInformationService.getNextDepartures(eq(serviceStop), eq(departureTime), any()
                , eq(limit))).thenReturn(stopTimes);
        List<Departure> stopTimeDtos = scheduleController.getDepartures(stopId, departureTime, limit, null);
        assertNotNull(stopTimeDtos);
    }

    @Test
    void testGetDepartures_withInvalidLimit() {
        int limit = 0;
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            scheduleController.getDepartures("stopId", null, limit, null);
        });
        assertEquals("Limit must be greater than 0", exception.getReason());
        assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
    }

}
