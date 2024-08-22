package ch.naviqore.app.controller;

import ch.naviqore.app.dto.Departure;
import ch.naviqore.app.dto.DistanceToStop;
import ch.naviqore.app.dto.SearchType;
import ch.naviqore.app.dto.Stop;
import ch.naviqore.service.ScheduleInformationService;
import ch.naviqore.service.Validity;
import ch.naviqore.service.exception.StopNotFoundException;
import ch.naviqore.utils.spatial.GeoCoordinate;
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
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static ch.naviqore.app.dto.DtoMapper.map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

            ch.naviqore.app.dto.ScheduleInfo routerInfo = scheduleController.getScheduleInfo();
            assertEquals(supportsAccessibility, routerInfo.hasAccessibility());
            assertEquals(supportsBikes, routerInfo.hasBikes());
            assertEquals(supportsTravelModes, routerInfo.hasTravelModes());
            assertEquals(expStartDate, routerInfo.getScheduleValidity().getStartDate());
            assertEquals(expEndDate, routerInfo.getScheduleValidity().getEndDate());
        }
    }

    @Nested
    class AutoCompleteStops {

        @Test
        void shouldSucceedWithValidQuery() {
            String query = "query";
            when(scheduleInformationService.getStops(query, map(SearchType.STARTS_WITH))).thenReturn(List.of());
            List<Stop> stops = scheduleController.getAutoCompleteStops(query, 10, SearchType.STARTS_WITH);

            assertNotNull(stops);
        }

        @Test
        void shouldFailWithNegativeLimit() {
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> scheduleController.getAutoCompleteStops("query", -10, SearchType.STARTS_WITH));

            assertEquals("Limit must be greater than 0", exception.getReason());
            assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
        }

        @Test
        void shouldFailWithZeroLimit() {
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> scheduleController.getAutoCompleteStops("query", 0, SearchType.STARTS_WITH));

            assertEquals("Limit must be greater than 0", exception.getReason());
            assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
        }

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

        @Test
        void shouldFailWithNegativeLimit() {
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> scheduleController.getNearestStops(0, 0, 1000, -10));

            assertEquals("Limit must be greater than 0", exception.getReason());
            assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
        }

        @Test
        void shouldFailWithZeroLimit() {
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> scheduleController.getNearestStops(0, 0, 1000, 0));

            assertEquals("Limit must be greater than 0", exception.getReason());
            assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
        }

        @Test
        void shouldFailWithNegativeMaxDistance() {
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> scheduleController.getNearestStops(0, 0, -1000, 10));

            assertEquals("Max distance cannot be negative", exception.getReason());
            assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
        }

        @ParameterizedTest(name = "Test case {index}: Latitude={0}, Longitude={1}")
        @CsvSource({"91, 0",    // Invalid latitude
                "-91, 0",   // Invalid latitude
                "0, 181",   // Invalid longitude
                "0, -181",  // Invalid longitude
                "91, 181",  // Invalid latitude and longitude
                "-91, -181" // Invalid latitude and longitude
        })
        void shouldFailWithInvalidCoordinates(double latitude, double longitude) {
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> scheduleController.getNearestStops(latitude, longitude, 1000, 10));
            assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
        }
    }

    @Nested
    class GetStop {

        @Test
        void shouldSucceedWithValidQuery() throws StopNotFoundException {
            String stopId = "stopId";
            ch.naviqore.service.Stop serviceStop = mock(ch.naviqore.service.Stop.class);
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

        @Test
        void shouldFailWithInvalidLimit() {
            int limit = 0;
            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> scheduleController.getDepartures("stopId", null, limit, null));
            assertEquals("Limit must be greater than 0", exception.getReason());
            assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
        }

        @Test
        void shouldSucceedWithNullUntilDateTime() throws StopNotFoundException {
            String stopId = "stopId";
            int limit = 10;
            LocalDateTime departureTime = LocalDateTime.now().plusDays(2);
            ch.naviqore.service.Stop serviceStop = mock(ch.naviqore.service.Stop.class);
            when(scheduleInformationService.getStopById(stopId)).thenReturn(serviceStop);
            List<ch.naviqore.service.StopTime> stopTimes = List.of();
            when(scheduleInformationService.getNextDepartures(eq(serviceStop), eq(departureTime), any(),
                    eq(limit))).thenReturn(stopTimes);
            List<Departure> stopTimeDtos = scheduleController.getDepartures(stopId, departureTime, limit, null);
            assertNotNull(stopTimeDtos);
        }

        @Test
        void shouldSucceedWithValidQuery() throws StopNotFoundException {
            String stopId = "stopId";
            ch.naviqore.service.Stop serviceStop = mock(ch.naviqore.service.Stop.class);
            when(scheduleInformationService.getStopById(stopId)).thenReturn(serviceStop);
            List<ch.naviqore.service.StopTime> stopTimes = List.of();
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
            ch.naviqore.service.Stop serviceStop = mock(ch.naviqore.service.Stop.class);
            when(scheduleInformationService.getStopById(stopId)).thenReturn(serviceStop);
            List<ch.naviqore.service.StopTime> stopTimes = List.of();
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
            ch.naviqore.service.Stop serviceStop = mock(ch.naviqore.service.Stop.class);
            when(scheduleInformationService.getStopById(stopId)).thenReturn(serviceStop);
            List<ch.naviqore.service.StopTime> stopTimes = List.of();
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

            ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                    () -> scheduleController.getDepartures(stopId, departureTime, 10, untilTime));
            assertEquals(HttpStatusCode.valueOf(400), exception.getStatusCode());
        }

    }

}
