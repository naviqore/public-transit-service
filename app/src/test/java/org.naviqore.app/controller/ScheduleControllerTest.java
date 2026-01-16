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
import org.naviqore.app.exception.InvalidParametersException;
import org.naviqore.app.exception.StopNotFoundException;
import org.naviqore.service.ScheduleInformationService;
import org.naviqore.service.Validity;
import org.naviqore.utils.spatial.GeoCoordinate;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
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
            PublicTransitServiceFake serviceFake = new PublicTransitServiceFake();
            serviceFake.setHasAccessibilityInformation(supportsAccessibility);
            serviceFake.setHasBikeInformation(supportsBikes);
            serviceFake.setHasTravelModeInformation(supportsTravelModes);

            LocalDate expStartDate = serviceFake.getValidity().getStartDate();
            LocalDate expEndDate = serviceFake.getValidity().getEndDate();

            ScheduleController scheduleController = new ScheduleController(serviceFake);

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

    }

    @Nested
    class GetNearestStops {

        @Test
        void shouldSucceedWithValidQuery() {
            GeoCoordinate location = new GeoCoordinate(0, 0);
            int radius = 1000;
            int limit = 10;

            when(scheduleInformationService.getNearestStops(location, radius)).thenReturn(List.of());

            List<DistanceToStop> stops = scheduleController.getNearestStops(location.latitude(), location.longitude(),
                    radius, limit);

            assertNotNull(stops);
        }

        @ParameterizedTest(name = "Test case {index}: Latitude={0}, Longitude={1}")
        @CsvSource({"91, 0", "-91, 0", "0, 181", "0, -181", "91, 181", "-91, -181"})
        void shouldFailWithInvalidCoordinates(double latitude, double longitude) {
            InvalidCoordinatesException exception = assertThrows(InvalidCoordinatesException.class,
                    () -> scheduleController.getNearestStops(latitude, longitude, 1000, 10));
            assertNotNull(exception.getMessage());
        }
    }

    @Nested
    class GetStop {

        @Test
        void shouldSucceedWithValidQuery() throws org.naviqore.service.exception.StopNotFoundException {
            String stopId = "stopId";
            org.naviqore.service.Stop serviceStop = mock(org.naviqore.service.Stop.class);
            when(scheduleInformationService.getStopById(stopId)).thenReturn(serviceStop);
            Stop stop = scheduleController.getStop(stopId);
            assertNotNull(stop);
        }

        @Test
        void shouldFailWithStopNotFoundException() throws org.naviqore.service.exception.StopNotFoundException {
            String stopId = "stopId";
            when(scheduleInformationService.getStopById(stopId)).thenThrow(
                    org.naviqore.service.exception.StopNotFoundException.class);
            StopNotFoundException exception = assertThrows(StopNotFoundException.class,
                    () -> scheduleController.getStop(stopId));
            assertEquals("Stop with ID 'stopId' not found.", exception.getMessage());
            assertEquals("stopId", exception.getStopId());
        }

    }

    @Nested
    class GetStopTimes {

        @Mock
        private Validity validity;

        @BeforeEach
        void setUp() {
            when(scheduleInformationService.getValidity()).thenReturn(validity);
            when(validity.isWithin(any(LocalDate.class))).thenReturn(true);
        }

        @Test
        void shouldSucceedWithNullUntilDateTime() throws org.naviqore.service.exception.StopNotFoundException {
            String stopId = "stopId";
            int limit = 10;
            OffsetDateTime fromTime = OffsetDateTime.now();
            OffsetDateTime expectedUntil = fromTime.plusHours(6);

            org.naviqore.service.Stop serviceStop = mock(org.naviqore.service.Stop.class);
            when(scheduleInformationService.getStopById(stopId)).thenReturn(serviceStop);
            when(scheduleInformationService.getStopTimes(any(), any(), any(), any())).thenReturn(List.of());

            List<Departure> result = scheduleController.getStopTimes(stopId, fromTime, null, limit, TimeType.DEPARTURE);

            assertNotNull(result);
            verify(scheduleInformationService).getStopTimes(eq(serviceStop), eq(fromTime), eq(expectedUntil),
                    eq(org.naviqore.service.TimeType.DEPARTURE));
        }

        @Test
        void shouldSucceedWithExplicitTimesAndLimit() throws org.naviqore.service.exception.StopNotFoundException {
            String stopId = "stopId";
            int limit = 1;
            OffsetDateTime fromTime = OffsetDateTime.now();
            OffsetDateTime untilTime = fromTime.plusMinutes(30);

            org.naviqore.service.Stop serviceStop = mock(org.naviqore.service.Stop.class);
            when(scheduleInformationService.getStopById(stopId)).thenReturn(serviceStop);
            org.naviqore.service.StopTime st1 = mock(org.naviqore.service.StopTime.class);
            org.naviqore.service.StopTime st2 = mock(org.naviqore.service.StopTime.class);
            org.naviqore.service.Trip trip1 = mock(org.naviqore.service.Trip.class, RETURNS_DEEP_STUBS);
            when(trip1.getRoute().getRouteType()).thenReturn(org.naviqore.service.TravelMode.BUS);
            when(st1.getStop()).thenReturn(serviceStop);
            when(st1.getTrip()).thenReturn(trip1);
            when(scheduleInformationService.getStopTimes(any(), any(), any(), any())).thenReturn(List.of(st1, st2));

            List<Departure> result = scheduleController.getStopTimes(stopId, fromTime, untilTime, limit,
                    TimeType.ARRIVAL);

            assertEquals(1, result.size());
            verify(scheduleInformationService).getStopTimes(eq(serviceStop), eq(fromTime), eq(untilTime),
                    eq(org.naviqore.service.TimeType.ARRIVAL));
        }

        @Test
        void shouldSucceedWithNullFromDateTime() throws org.naviqore.service.exception.StopNotFoundException {
            String stopId = "stopId";

            org.naviqore.service.Stop serviceStop = mock(org.naviqore.service.Stop.class);
            when(scheduleInformationService.getStopById(stopId)).thenReturn(serviceStop);
            when(scheduleInformationService.getStopTimes(any(), any(), any(), any())).thenReturn(
                    Collections.emptyList());

            scheduleController.getStopTimes(stopId, null, null, 10, TimeType.DEPARTURE);

            verify(scheduleInformationService).getStopTimes(eq(serviceStop), any(OffsetDateTime.class),
                    any(OffsetDateTime.class), eq(org.naviqore.service.TimeType.DEPARTURE));
        }

        @Test
        void shouldFailWithStopNotFoundException() throws org.naviqore.service.exception.StopNotFoundException {
            String stopId = "stopId";
            when(scheduleInformationService.getStopById(stopId)).thenThrow(
                    org.naviqore.service.exception.StopNotFoundException.class);

            StopNotFoundException exception = assertThrows(StopNotFoundException.class,
                    () -> scheduleController.getStopTimes(stopId, null, null, 10, TimeType.DEPARTURE));

            assertEquals("Stop with ID 'stopId' not found.", exception.getMessage());
        }

        @Test
        void shouldFailWithUntilDateTimeBeforeFromDateTime() {
            String stopId = "stopId";
            OffsetDateTime from = OffsetDateTime.now();
            OffsetDateTime until = from.minusMinutes(1);

            InvalidParametersException exception = assertThrows(InvalidParametersException.class,
                    () -> scheduleController.getStopTimes(stopId, from, until, 10, TimeType.DEPARTURE));
            assertEquals("Until date time must be after departure date time.", exception.getMessage());
        }
    }
}