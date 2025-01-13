package ch.naviqore.service.gtfs.raptor;

import ch.naviqore.gtfs.schedule.GtfsScheduleReader;
import ch.naviqore.gtfs.schedule.GtfsScheduleTestData;
import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.service.*;
import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.service.config.ServiceConfig;
import ch.naviqore.service.exception.*;
import ch.naviqore.service.gtfs.raptor.convert.GtfsToRaptorTestSchedule;
import ch.naviqore.service.repo.GtfsScheduleRepository;
import ch.naviqore.utils.spatial.GeoCoordinate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static ch.naviqore.service.config.ServiceConfig.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class GtfsRaptorServiceIT {

    private GtfsRaptorService service;

    @Nested
    class FromZip {

        @BeforeEach
        void setUp(@TempDir Path tempDir) throws IOException, InterruptedException {
            File zipFile = GtfsScheduleTestData.prepareZipDataset(tempDir);

            // implement repo for gtfs schedule file reader
            GtfsScheduleRepository repo = () -> new GtfsScheduleReader().read(zipFile.toString());
            GtfsRaptorServiceInitializer initializer = new GtfsRaptorServiceInitializer(
                    new ServiceConfig(zipFile.getAbsolutePath()), repo.get());

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
                void shouldFindStopByName() {
                    List<Stop> stops = service.getStops("Furnace Creek Resort", SearchType.CONTAINS);
                    assertFalse(stops.isEmpty(), "Expected to find stops matching the name.");
                    assertEquals("Furnace Creek Resort (Demo)", stops.getFirst().getName());
                }

                @Test
                void shouldNotFindNonExistingStopByName() {
                    List<Stop> stops = service.getStops("NonExistingStop", SearchType.CONTAINS);
                    assertTrue(stops.isEmpty(), "Expected no stops to be found.");
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
                            Integer.MAX_VALUE, Integer.MAX_VALUE);
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
                config = new ConnectionQueryConfig(10 * 60, 2 * 60, 4, 24 * 60 * 60, false, false, null);
            }

            @Nested
            class Connections {

                @Nested
                class FromLocation {

                    @Test
                    void shouldGetConnections() throws ConnectionRoutingException {
                        List<Connection> connections = service.getConnections(new GeoCoordinate(36.425288, -117.133162),
                                new GeoCoordinate(36.88108, -116.81797), LocalDateTime.of(2008, 5, 15, 8, 0),
                                TimeType.DEPARTURE, config);
                        assertFalse(connections.isEmpty(), "Expected to find connections.");
                    }

                    @Test
                    void shouldHandleNoNearestTargetStop() throws ConnectionRoutingException {
                        List<Connection> connections = service.getConnections(new GeoCoordinate(0.0, 0.0),
                                new GeoCoordinate(36.88108, -116.81797), LocalDateTime.of(2008, 5, 15, 8, 0),
                                TimeType.DEPARTURE, config);
                        assertTrue(connections.isEmpty(),
                                "Expected no connections to be found when no nearest stop exists.");
                    }

                    @Test
                    void shouldHandleInactiveDate() throws ConnectionRoutingException {
                        List<Connection> connections = service.getConnections(new GeoCoordinate(36.425288, -117.133162),
                                new GeoCoordinate(36.88108, -116.81797), LocalDateTime.of(2023, 5, 15, 8, 0),
                                TimeType.DEPARTURE, config);
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
                                new GeoCoordinate(36.88108, -116.81797), LocalDateTime.of(2008, 5, 15, 8, 0),
                                TimeType.DEPARTURE, config);
                        assertFalse(connections.isEmpty(), "Expected to find connections.");
                    }

                    @Test
                    void shouldHandleNoNearestTargetStop() throws ConnectionRoutingException {
                        List<Connection> connections = service.getConnections(source, new GeoCoordinate(-89, 0),
                                LocalDateTime.of(2008, 5, 15, 8, 0), TimeType.DEPARTURE, config);
                        assertTrue(connections.isEmpty(),
                                "Expected no connections to be found when no nearest stop exists.");
                    }

                    @Test
                    void shouldHandleInactiveDate() throws ConnectionRoutingException {
                        List<Connection> connections = service.getConnections(source,
                                new GeoCoordinate(36.88108, -116.81797), LocalDateTime.of(2023, 5, 15, 8, 0),
                                TimeType.DEPARTURE, config);
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
                                new GeoCoordinate(36.425288, -117.133162), LocalDateTime.of(2008, 5, 15, 8, 0),
                                TimeType.DEPARTURE, config);
                        assertFalse(connections.isEmpty(), "Expected to find connections.");
                    }

                    @Test
                    void shouldHandleNoIsolinesFound() throws ConnectionRoutingException {
                        Map<Stop, Connection> connections = service.getIsolines(new GeoCoordinate(0.0, 0.0),
                                LocalDateTime.of(2008, 5, 15, 8, 0), TimeType.DEPARTURE, config);
                        assertTrue(connections.isEmpty(),
                                "Expected no isolines to be found when no nearest stop exists.");
                    }

                    @Test
                    void shouldHandleInactiveDate() throws ConnectionRoutingException {
                        Map<Stop, Connection> connections = service.getIsolines(
                                new GeoCoordinate(36.425288, -117.133162), LocalDateTime.of(2023, 5, 15, 8, 0),
                                TimeType.DEPARTURE, config);
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
                        Map<Stop, Connection> connections = service.getIsolines(source,
                                LocalDateTime.of(2008, 5, 15, 8, 0), TimeType.DEPARTURE, config);
                        assertFalse(connections.isEmpty(), "Expected to find connections.");
                    }

                    @Test
                    void shouldHandleInactiveDate() throws ConnectionRoutingException {
                        Map<Stop, Connection> connections = service.getIsolines(source,
                                LocalDateTime.of(2023, 5, 15, 8, 0), TimeType.DEPARTURE, config);
                        assertTrue(connections.isEmpty(),
                                "Expected no isolines to be found when no active trips exist on the date.");
                    }
                }
            }
        }
    }

    @Nested
    class ManualSchedule {

        private static final LocalDateTime DATE_TIME = LocalDateTime.MIN;
        private static final ConnectionQueryConfig QUERY_CONFIG = new ConnectionQueryConfig(10 * 60, 2 * 60, 4,
                24 * 60 * 60, false, false, null);

        @BeforeEach
        void setUp() {
            GtfsToRaptorTestSchedule builder = new GtfsToRaptorTestSchedule();
            GtfsSchedule schedule = builder.build();

            // do not create any transfers since all coordinates in the test schedule are at origin 0,0.
            ServiceConfig serviceConfig = new ServiceConfig("NONE", DEFAULT_GTFS_STATIC_UPDATE_CRON,
                    DEFAULT_TRANSFER_TIME_SAME_STOP_DEFAULT, -1, DEFAULT_TRANSFER_TIME_ACCESS_EGRESS,
                    DEFAULT_WALKING_SEARCH_RADIUS, DEFAULT_WALKING_CALCULATOR_TYPE, DEFAULT_WALKING_SPEED,
                    DEFAULT_WALKING_DURATION_MINIMUM, DEFAULT_MAX_DAYS_TO_SCAN, DEFAULT_RAPTOR_RANGE,
                    DEFAULT_CACHE_SIZE, DEFAULT_CACHE_EVICTION_STRATEGY);

            GtfsRaptorServiceInitializer initializer = new GtfsRaptorServiceInitializer(serviceConfig, schedule);

            service = initializer.get();
        }

        @Nested
        class Connection {

            @Test
            void shouldThrowOnInvalidStop() {
                assertThrows(StopNotFoundException.class,
                        () -> service.getConnections(service.getStopById("NOT_A_STOP"), service.getStopById("C"),
                                DATE_TIME, TimeType.DEPARTURE, QUERY_CONFIG));
            }

            @Nested
            class StopWithoutDepartures {

                @Test
                void target_departure() throws ConnectionRoutingException, StopNotFoundException {
                    List<ch.naviqore.service.Connection> connections = service.getConnections(service.getStopById("A"),
                            service.getStopById("D"), DATE_TIME, TimeType.DEPARTURE, QUERY_CONFIG);

                    assertThat(connections).isEmpty();
                }

                @Test
                void target_arrival() throws ConnectionRoutingException, StopNotFoundException {
                    List<ch.naviqore.service.Connection> connections = service.getConnections(service.getStopById("A"),
                            service.getStopById("D"), DATE_TIME, TimeType.ARRIVAL, QUERY_CONFIG);

                    assertThat(connections).isEmpty();
                }

                @Test
                void source_departure() throws ConnectionRoutingException, StopNotFoundException {
                    List<ch.naviqore.service.Connection> connections = service.getConnections(service.getStopById("D"),
                            service.getStopById("A"), DATE_TIME, TimeType.DEPARTURE, QUERY_CONFIG);

                    assertThat(connections).isEmpty();
                }

                @Test
                void source_arrival() throws ConnectionRoutingException, StopNotFoundException {
                    List<ch.naviqore.service.Connection> connections = service.getConnections(service.getStopById("D"),
                            service.getStopById("A"), DATE_TIME, TimeType.ARRIVAL, QUERY_CONFIG);

                    assertThat(connections).isEmpty();
                }
            }
        }

        @Nested
        class Isolines {

            @Test
            void shouldThrowOnInvalidStop() {
                assertThrows(StopNotFoundException.class,
                        () -> service.getIsolines(service.getStopById("NOT_A_STOP"), DATE_TIME, TimeType.DEPARTURE,
                                QUERY_CONFIG));
            }

            @Nested
            class StopWithoutDepartures {

                @Test
                void departure() throws ConnectionRoutingException, StopNotFoundException {
                    Map<Stop, ch.naviqore.service.Connection> isolines = service.getIsolines(service.getStopById("D"),
                            DATE_TIME, TimeType.DEPARTURE, QUERY_CONFIG);
                    assertThat(isolines).isEmpty();
                }

                @Test
                void arrival() throws ConnectionRoutingException, StopNotFoundException {
                    Map<Stop, ch.naviqore.service.Connection> isolines = service.getIsolines(service.getStopById("D"),
                            DATE_TIME, TimeType.ARRIVAL, QUERY_CONFIG);
                    assertThat(isolines).isEmpty();
                }
            }

        }

    }

}
