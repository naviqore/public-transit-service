package org.naviqore.service.gtfs.raptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.naviqore.gtfs.schedule.GtfsScheduleDataset;
import org.naviqore.gtfs.schedule.GtfsScheduleReader;
import org.naviqore.service.*;
import org.naviqore.service.config.ConnectionQueryConfig;
import org.naviqore.service.config.ServiceConfig;
import org.naviqore.service.exception.*;
import org.naviqore.service.repo.GtfsScheduleRepository;
import org.naviqore.utils.spatial.GeoCoordinate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class GtfsRaptorServiceIT {

    private static final ZoneId ZONE_ID = ZoneId.of("America/Los_Angeles");
    private GtfsRaptorService service;

    private static OffsetDateTime at(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(ZONE_ID).toOffsetDateTime();
    }

    @Nested
    class FromZip {

        @BeforeEach
        void setUp(@TempDir Path tempDir) throws IOException, InterruptedException {
            File zipFile = GtfsScheduleDataset.SAMPLE_FEED_1.getZip(tempDir);

            // implement repo for gtfs schedule file reader
            GtfsScheduleRepository repo = () -> new GtfsScheduleReader().read(zipFile.toString());
            GtfsRaptorServiceInitializer initializer = new GtfsRaptorServiceInitializer(
                    ServiceConfig.builder().gtfsScheduleRepository(repo).build());

            service = initializer.get();
        }

        @Nested
        class Validity {

            @Test
            void shouldReturnCorrectStartDate() {
                LocalDate expectedStartDate = LocalDate.of(2007, 1, 1);
                assertEquals(expectedStartDate, service.getValidity().getStartDate(),
                        "The start date of the validity should match the earliest start date in the schedule.");
            }

            @Test
            void shouldReturnCorrectEndDate() {
                LocalDate expectedEndDate = LocalDate.of(2010, 12, 31);
                assertEquals(expectedEndDate, service.getValidity().getEndDate(),
                        "The end date of the validity should match the latest end date in the schedule.");
            }

            @Test
            void shouldBeWithinValidityPeriod() {
                LocalDate dateWithinValidity = LocalDate.of(2008, 6, 1);
                assertTrue(service.getValidity().isWithin(dateWithinValidity),
                        "The date should be within the validity period.");
            }

            @Test
            void shouldIncludeStartDateInValidityPeriod() {
                LocalDate startDate = service.getValidity().getStartDate();
                assertTrue(service.getValidity().isWithin(startDate),
                        "The start date should be considered within the validity period.");
            }

            @Test
            void shouldIncludeEndDateInValidityPeriod() {
                LocalDate endDate = service.getValidity().getEndDate();
                assertTrue(service.getValidity().isWithin(endDate),
                        "The end date should be considered within the validity period.");
            }

            @Test
            void shouldNotBeWithinValidityPeriodBeforeStart() {
                LocalDate dateBeforeValidity = LocalDate.of(2006, 12, 13);
                assertFalse(service.getValidity().isWithin(dateBeforeValidity),
                        "The date before the start date should not be within the validity period.");
            }

            @Test
            void shouldNotBeWithinValidityPeriodAfterEnd() {
                LocalDate dateAfterValidity = LocalDate.of(2011, 1, 1);
                assertFalse(service.getValidity().isWithin(dateAfterValidity),
                        "The date after the end date should not be within the validity period.");
            }
        }

        @Nested
        class ScheduleInformation {

            @Nested
            class SearchStopByName {

                @Test
                void shouldSortResultsAlphabetically() {
                    String query = "a";
                    List<String> expectedOrder = List.of("Amargosa Valley (Demo)", "Doing Ave / D Ave N (Demo)",
                            "E Main St / S Irving St (Demo)", "Furnace Creek Resort (Demo)",
                            "North Ave / D Ave N (Demo)", "North Ave / N A Ave (Demo)", "Nye County Airport (Demo)",
                            "Stagecoach Hotel & Casino (Demo)");

                    List<String> actualOrder = service.getStops(query, SearchType.CONTAINS,
                            StopSortStrategy.ALPHABETICAL).stream().map(Stop::getName).collect(Collectors.toList());

                    assertFalse(actualOrder.isEmpty());
                    assertEquals(expectedOrder, actualOrder, "Stops should be sorted alphabetically by name.");
                }

                @Test
                void shouldFindStopByName() {
                    List<Stop> stops = service.getStops("Furnace Creek Resort", SearchType.CONTAINS,
                            StopSortStrategy.RELEVANCE);
                    assertFalse(stops.isEmpty(), "Expected to find stops matching the name.");
                    assertEquals("Furnace Creek Resort (Demo)", stops.getFirst().getName());
                }

                @Test
                void shouldNotFindNonExistingStopByName() {
                    List<Stop> stops = service.getStops("NonExistingStop", SearchType.CONTAINS,
                            StopSortStrategy.RELEVANCE);
                    assertTrue(stops.isEmpty(), "Expected no stops to be found for a non-existing name.");
                }

                @Test
                void shouldSortResultsByRelevance() {
                    String query = "North Ave";
                    List<String> expectedOrder = List.of("North Ave / D Ave N (Demo)", "North Ave / N A Ave (Demo)");

                    List<String> actualOrder = service.getStops(query, SearchType.CONTAINS, StopSortStrategy.RELEVANCE)
                            .stream()
                            .map(Stop::getName)
                            .toList();

                    assertFalse(actualOrder.isEmpty(), "Expected to find stops for the query.");
                    assertEquals(expectedOrder, actualOrder,
                            "Stops should be sorted by relevance (starts with, then length).");
                }

                @Test
                void shouldPlaceExactMatchFirstWithRelevanceSort() {
                    String query = "Bullfrog (Demo)";
                    List<Stop> stops = service.getStops(query, SearchType.CONTAINS, StopSortStrategy.RELEVANCE);

                    assertFalse(stops.isEmpty());
                    assertEquals("Bullfrog (Demo)", stops.getFirst().getName(),
                            "The exact match should be the first result.");
                }
            }

            @Nested
            class NearestStop {
                @Test
                void shouldFindNearestStop() {
                    Stop stop = service.getNearestStop(new GeoCoordinate(36.425288, -117.133162)).orElseThrow();
                    assertEquals("Furnace Creek Resort (Demo)", stop.getName());
                }

                @Test
                void shouldFindNearestStops() {
                    List<Stop> stops = service.getNearestStops(new GeoCoordinate(36.425288, -117.133162),
                            Integer.MAX_VALUE);
                    assertFalse(stops.isEmpty(), "Expected to find nearest stops.");
                    assertTrue(stops.size() > 1, "Expected to find more than one stop.");
                }
            }

            @Nested
            class GetById {

                @Test
                void shouldFindStopById() throws StopNotFoundException {
                    Stop stop = service.getStopById("FUR_CREEK_RES");
                    assertEquals("Furnace Creek Resort (Demo)", stop.getName());
                }

                @Test
                void shouldNotFindMissingStopById() {
                    assertThrows(StopNotFoundException.class, () -> service.getStopById("NON_EXISTENT_STOP"));
                }

                @Test
                void shouldFindActiveTripById() throws TripNotFoundException, TripNotActiveException {
                    Trip trip = service.getTripById("AB1", LocalDate.of(2008, 5, 15));
                    assertNotNull(trip);
                    assertEquals("to Bullfrog", trip.getHeadSign());
                }

                @Test
                void shouldNotFindInactiveTripById() {
                    assertThrows(TripNotActiveException.class,
                            () -> service.getTripById("AB1", LocalDate.of(2023, 5, 15)));
                }

                @Test
                void shouldNotFindMissingTripById() {
                    assertThrows(TripNotFoundException.class,
                            () -> service.getTripById("NON_EXISTENT_TRIP", LocalDate.of(2023, 5, 15)));
                }

                @Test
                void shouldThrowTripNotActiveException() {
                    assertThrows(TripNotActiveException.class,
                            () -> service.getTripById("AAMV1", LocalDate.of(2023, 5, 15)));
                }

                @Test
                void shouldFindRouteById() throws RouteNotFoundException {
                    Route route = service.getRouteById("AB");
                    assertNotNull(route);
                    assertEquals("Airport - Bullfrog", route.getName());
                }

                @Test
                void shouldNotFindMissingRouteById() {
                    assertThrows(RouteNotFoundException.class, () -> service.getRouteById("NON_EXISTENT_ROUTE"));
                }
            }
        }

        @Nested
        class Routing {

            private ConnectionQueryConfig config;

            @BeforeEach
            void setUp() {
                config = ConnectionQueryConfig.builder()
                        .maximumWalkDuration(10 * 60)
                        .minimumTransferDuration(2 * 60)
                        .maximumTransfers(4)
                        .maximumTravelDuration(24 * 60 * 60)
                        .wheelchairAccessible(false)
                        .bikeAllowed(false)
                        .build();
            }

            @Nested
            class Connections {

                @Nested
                class FromLocation {

                    @Test
                    void shouldGetConnections() throws ConnectionRoutingException {
                        List<Connection> connections = service.getConnections(new GeoCoordinate(36.425288, -117.133162),
                                new GeoCoordinate(36.88108, -116.81797), at(2008, 5, 15, 8, 0), TimeType.DEPARTURE,
                                config);
                        assertFalse(connections.isEmpty(), "Expected to find connections.");
                    }

                    @Test
                    void shouldHandleNoNearestTargetStop() throws ConnectionRoutingException {
                        List<Connection> connections = service.getConnections(new GeoCoordinate(0.0, 0.0),
                                new GeoCoordinate(36.88108, -116.81797), at(2008, 5, 15, 8, 0), TimeType.DEPARTURE,
                                config);
                        assertTrue(connections.isEmpty(),
                                "Expected no connections to be found when no nearest stop exists.");
                    }

                    @Test
                    void shouldHandleInactiveDate() throws ConnectionRoutingException {
                        List<Connection> connections = service.getConnections(new GeoCoordinate(36.425288, -117.133162),
                                new GeoCoordinate(36.88108, -116.81797), at(2023, 5, 15, 8, 0), TimeType.DEPARTURE,
                                config);
                        assertTrue(connections.isEmpty(),
                                "Expected no connections to be found when source stop has no active trip on the date.");
                    }
                }

                @Nested
                class FromStop {

                    private Stop source;

                    @BeforeEach
                    void setUp() throws StopNotFoundException {
                        source = service.getStopById("FUR_CREEK_RES");
                    }

                    @Test
                    void shouldGetConnections() throws ConnectionRoutingException {
                        List<Connection> connections = service.getConnections(source,
                                new GeoCoordinate(36.88108, -116.81797), at(2008, 5, 15, 8, 0), TimeType.DEPARTURE,
                                config);
                        assertFalse(connections.isEmpty(), "Expected to find connections.");
                    }

                    @Test
                    void shouldHandleNoNearestTargetStop() throws ConnectionRoutingException {
                        List<Connection> connections = service.getConnections(source, new GeoCoordinate(-89, 0),
                                at(2008, 5, 15, 8, 0), TimeType.DEPARTURE, config);
                        assertTrue(connections.isEmpty(),
                                "Expected no connections to be found when no nearest stop exists.");
                    }

                    @Test
                    void shouldHandleInactiveDate() throws ConnectionRoutingException {
                        List<Connection> connections = service.getConnections(source,
                                new GeoCoordinate(36.88108, -116.81797), at(2023, 5, 15, 8, 0), TimeType.DEPARTURE,
                                config);
                        assertTrue(connections.isEmpty(),
                                "Expected no connections to be found when source stop has no active trip on the date.");
                    }
                }
            }

            @Nested
            class Isoline {

                @Nested
                class FromLocation {

                    @Test
                    void shouldGetIsolines() throws ConnectionRoutingException {
                        Map<Stop, Connection> connections = service.getIsolines(
                                new GeoCoordinate(36.425288, -117.133162), at(2008, 5, 15, 8, 0), TimeType.DEPARTURE,
                                config);
                        assertFalse(connections.isEmpty(), "Expected to find connections.");
                    }

                    @Test
                    void shouldHandleNoIsolinesFound() throws ConnectionRoutingException {
                        Map<Stop, Connection> connections = service.getIsolines(new GeoCoordinate(0.0, 0.0),
                                at(2008, 5, 15, 8, 0), TimeType.DEPARTURE, config);
                        assertTrue(connections.isEmpty(),
                                "Expected no isolines to be found when no nearest stop exists.");
                    }

                    @Test
                    void shouldHandleInactiveDate() throws ConnectionRoutingException {
                        Map<Stop, Connection> connections = service.getIsolines(
                                new GeoCoordinate(36.425288, -117.133162), at(2023, 5, 15, 8, 0), TimeType.DEPARTURE,
                                config);
                        assertTrue(connections.isEmpty(),
                                "Expected no isolines to be found when no active trips exist on the date.");
                    }
                }

                @Nested
                class FromStop {

                    private Stop source;

                    @BeforeEach
                    void setUp() throws StopNotFoundException {
                        source = service.getStopById("FUR_CREEK_RES");
                    }

                    @Test
                    void shouldGetIsolines() throws ConnectionRoutingException {
                        Map<Stop, Connection> connections = service.getIsolines(source, at(2008, 5, 15, 8, 0),
                                TimeType.DEPARTURE, config);
                        assertFalse(connections.isEmpty(), "Expected to find connections.");
                    }

                    @Test
                    void shouldHandleInactiveDate() throws ConnectionRoutingException {
                        Map<Stop, Connection> connections = service.getIsolines(source, at(2023, 5, 15, 8, 0),
                                TimeType.DEPARTURE, config);
                        assertTrue(connections.isEmpty(),
                                "Expected no isolines to be found when no active trips exist on the date.");
                    }
                }
            }
        }
    }
}
