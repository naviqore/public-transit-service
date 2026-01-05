package org.naviqore.app.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.naviqore.app.dto.*;
import org.naviqore.app.exception.InvalidCoordinatesException;
import org.naviqore.app.exception.InvalidRoutingParametersException;
import org.naviqore.service.ScheduleInformationService;
import org.naviqore.service.Validity;
import org.naviqore.service.exception.StopNotFoundException;
import org.naviqore.utils.spatial.GeoCoordinate;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.naviqore.app.dto.DtoMapper.map;

@ExtendWith(MockitoExtension.class)
public class ScheduleControllerTest {

    @Mock
    private ScheduleInformationService scheduleInformationService;

    @InjectMocks
    private ScheduleController scheduleController;

    @Nested
    class ScheduleInfo {
        static Stream<Arguments> provideScheduleInfoTestCombinations() {
            boolean[] supportsAccessibility = {true, false};
            boolean[] supportsBikes = {true, false};
            boolean[] supportsTravelModes = {true, false};

            // create all permutations based on the boolean arrays (not hard coded)
            Stream<Arguments> stream = Stream.of();
            for (boolean supportsAccessibilityValue : supportsAccessibility) {
                for (boolean supportsBikesValue : supportsBikes) {
                    for (boolean supportsTravelModesValue : supportsTravelModes) {
                        stream = Stream.concat(stream, Stream.of(
                                Arguments.of(supportsAccessibilityValue, supportsBikesValue,
                                        supportsTravelModesValue)));
                    }
                }
            }
            return stream;
        }

        @ParameterizedTest(name = "scheduleInfo_{index}")
        @MethodSource("provideScheduleInfoTestCombinations")
        void testQueryConfigValues(boolean supportsAccessibility, boolean supportsBikes, boolean supportsTravelModes) {
            DummyService dummyService = new DummyService();
            dummyService.setHasAccessibilityInformation(supportsAccessibility);
            dummyService.setHasBikeInformation(supportsBikes);
            dummyService.setHasTravelModeInformation(supportsTravelModes);

            LocalDate expStartDate = dummyService.getValidity().getStartDate();
            LocalDate expEndDate = dummyService.getValidity().getEndDate();

            ScheduleController scheduleController = new ScheduleController(dummyService);

            org.naviqore.app.dto.ScheduleInfo routerInfo = scheduleController.getScheduleInfo();
            assertEquals(supportsAccessibility, routerInfo.isHasAccessibility());
            assertEquals(supportsBikes, routerInfo.isHasBikes());
            assertEquals(supportsTravelModes, routerInfo.isHasTravelModes());
            assertEquals(expStartDate, routerInfo.getScheduleValidity().getStartDate());
            assertEquals(expEndDate, routerInfo.getScheduleValidity().getEndDate());
        }
    }

    @Nested
    class AutoCompleteStops {

        @Test
        void shouldSucceedWithValidQuery() {
            String query = "query";
            when(scheduleInformationService.getStops(query, map(SearchType.STARTS_WITH),
                    map(StopSortStrategy.ALPHABETICAL))).thenReturn(List.of());
            List<Stop> stops = scheduleController.getAutoCompleteStops(query, 10, SearchType.STARTS_WITH,
                    StopSortStrategy.ALPHABETICAL);

            assertNotNull(stops);
        }

        // Note: Limit validation is now handled by Bean Validation (@Min(1)) at the framework level
        // These tests would need to be integration tests to verify ConstraintViolationException handling

    }

    @Nested
    class GetNearestStops {

        @Test
        void shouldSucceedWithValidQuery() {
            GeoCoordinate location = new GeoCoordinate(0, 0);
            int radius = 1000;
            int limit = 10;

            when(scheduleInformationService.getNearestStops(location, radius, limit)).thenReturn(List.of());

            List<DistanceToStop> stops = scheduleController.getNearestStops(location.latitude(), location.longitude(),
                    radius, limit);

            assertNotNull(stops);
        }

        // Note: Limit and maxDistance validation is now handled by Bean Validation (@Min) at the framework level
        // These tests would need to be integration tests to verify ConstraintViolationException handling

        @ParameterizedTest(name = "Test case {index}: Latitude={0}, Longitude={1}")
        @CsvSource({"91, 0",    // Invalid latitude
                "-91, 0",   // Invalid latitude
                "0, 181",   // Invalid longitude
                "0, -181",  // Invalid longitude
                "91, 181",  // Invalid latitude and longitude
                "-91, -181" // Invalid latitude and longitude
        })
        void shouldFailWithInvalidCoordinates(double latitude, double longitude) {
            InvalidCoordinatesException exception = assertThrows(InvalidCoordinatesException.class,
                    () -> scheduleController.getNearestStops(latitude, longitude, 1000, 10));
            assertNotNull(exception.getMessage());
        }
    }

    @Nested
    class GetStop {

        @Test
        void shouldSucceedWithValidQuery() throws StopNotFoundException {
            String stopId = "stopId";
            org.naviqore.service.Stop serviceStop = mock(org.naviqore.service.Stop.class);
            when(scheduleInformationService.getStopById(stopId)).thenReturn(serviceStop);
            Stop stop = scheduleController.getStop(stopId);
            assertNotNull(stop);
        }

        @Test
        void shouldFailWithStopNotFoundException() throws StopNotFoundException {
            String stopId = "stopId";
            when(scheduleInformationService.getStopById(stopId)).thenThrow(StopNotFoundException.class);
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> scheduleController.getStop(stopId));
            assertEquals(HttpStatusCode.valueOf(404), exception.getStatusCode());
        }

    }

    @Nested
    class GetDepartures {

        @Mock
        private Validity validity;

        @BeforeEach
        void setUp() {
            when(scheduleInformationService.getValidity()).thenReturn(validity);
            when(validity.isWithin(any(LocalDate.class))).thenReturn(true);
        }

        // Note: Limit validation is now handled by Bean Validation (@Min(1)) at the framework level
        // This test would need to be an integration test to verify ConstraintViolationException handling

        @Test
        void shouldSucceedWithNullUntilDateTime() throws StopNotFoundException {
            String stopId = "stopId";
            int limit = 10;
            LocalDateTime departureTime = LocalDateTime.now().plusDays(2);
            org.naviqore.service.Stop serviceStop = mock(org.naviqore.service.Stop.class);
            when(scheduleInformationService.getStopById(stopId)).thenReturn(serviceStop);
            List<org.naviqore.service.StopTime> stopTimes = List.of();
            when(scheduleInformationService.getNextDepartures(eq(serviceStop), eq(departureTime), any(),
                    eq(limit))).thenReturn(stopTimes);
            List<Departure> stopTimeDtos = scheduleController.getDepartures(stopId, departureTime, limit, null);
            assertNotNull(stopTimeDtos);
        }

        @Test
        void shouldSucceedWithValidQuery() throws StopNotFoundException {
            String stopId = "stopId";
            org.naviqore.service.Stop serviceStop = mock(org.naviqore.service.Stop.class);
            when(scheduleInformationService.getStopById(stopId)).thenReturn(serviceStop);
            List<org.naviqore.service.StopTime> stopTimes = List.of();
            when(scheduleInformationService.getNextDepartures(eq(serviceStop), any(LocalDateTime.class), eq(null),
                    eq(10))).thenReturn(stopTimes);
            List<Departure> stopTimeDtos = scheduleController.getDepartures(stopId, null, 10, null);
            assertNotNull(stopTimeDtos);
        }

        @Test
        void shouldSucceedWithNullDepartureDateTime() throws StopNotFoundException {
            String stopId = "stopId";
            int limit = 10;
            LocalDateTime untilTime = LocalDateTime.now().plusMinutes(1);
            org.naviqore.service.Stop serviceStop = mock(org.naviqore.service.Stop.class);
            when(scheduleInformationService.getStopById(stopId)).thenReturn(serviceStop);
            List<org.naviqore.service.StopTime> stopTimes = List.of();
            when(scheduleInformationService.getNextDepartures(eq(serviceStop), any(), eq(untilTime),
                    eq(limit))).thenReturn(stopTimes);
            List<Departure> stopTimeDtos = scheduleController.getDepartures(stopId, null, limit, untilTime);
            assertNotNull(stopTimeDtos);
        }

        @Test
        void shouldSucceedWithDepartureDateTime() throws StopNotFoundException {
            String stopId = "stopId";
            int limit = 10;
            LocalDateTime departureTime = LocalDateTime.now().plusDays(2);
            LocalDateTime untilTime = departureTime.plusMinutes(1);
            org.naviqore.service.Stop serviceStop = mock(org.naviqore.service.Stop.class);
            when(scheduleInformationService.getStopById(stopId)).thenReturn(serviceStop);
            List<org.naviqore.service.StopTime> stopTimes = List.of();
            when(scheduleInformationService.getNextDepartures(eq(serviceStop), eq(departureTime), eq(untilTime),
                    eq(limit))).thenReturn(stopTimes);
            List<Departure> stopTimeDtos = scheduleController.getDepartures(stopId, departureTime, limit, untilTime);
            assertNotNull(stopTimeDtos);
        }

        @Test
        void shouldFailWithStopNotFoundException() throws StopNotFoundException {
            String stopId = "stopId";
            when(scheduleInformationService.getStopById(stopId)).thenThrow(StopNotFoundException.class);
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> scheduleController.getDepartures(stopId, null, 10, null));
            assertEquals(HttpStatusCode.valueOf(404), exception.getStatusCode());
        }

        @Test
        void shouldFailWithUntilDateTimeBeforeDepartureDateTime() {
            String stopId = "stopId";
            LocalDateTime departureTime = LocalDateTime.now();
            LocalDateTime untilTime = departureTime.minusMinutes(1);

            InvalidRoutingParametersException exception = assertThrows(InvalidRoutingParametersException.class,
                    () -> scheduleController.getDepartures(stopId, departureTime, 10, untilTime));
            assertEquals("Until date time must be after departure date time.", exception.getMessage());
        }

    }

}
