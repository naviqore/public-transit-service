package ch.naviqore.service.gtfs.raptor;

import ch.naviqore.gtfs.schedule.GtfsScheduleReader;
import ch.naviqore.gtfs.schedule.GtfsScheduleTestData;
import ch.naviqore.service.*;
import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.service.config.ServiceConfig;
import ch.naviqore.service.exception.RouteNotFoundException;
import ch.naviqore.service.exception.StopNotFoundException;
import ch.naviqore.service.exception.TripNotActiveException;
import ch.naviqore.service.exception.TripNotFoundException;
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

import static org.junit.jupiter.api.Assertions.*;

class GtfsRaptorServiceIT {

    private GtfsRaptorService service;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException, InterruptedException {
        File zipFile = GtfsScheduleTestData.prepareZipDataset(tempDir);

        // implement repo for gtfs schedule file reader
        GtfsScheduleRepository repo = () -> new GtfsScheduleReader().read(zipFile.toString());
        GtfsRaptorServiceInitializer initializer = new GtfsRaptorServiceInitializer(
                new ServiceConfig(zipFile.getAbsolutePath()), repo.get());

        // create service from initialized data
        service = new GtfsRaptorService(initializer.getConfig(), initializer.getSchedule(),
                initializer.getSpatialStopIndex(), initializer.getStopSearchIndex(), initializer.getWalkCalculator(),
                initializer.getAdditionalTransfers());
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
                List<Stop> stops = service.getNearestStops(new GeoCoordinate(36.425288, -117.133162), Integer.MAX_VALUE,
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
                assertThrows(TripNotActiveException.class, () -> service.getTripById("AB1", LocalDate.of(2023, 5, 15)));
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
            config = new ConnectionQueryConfig(10 * 60, 2 * 60, 4, 24 * 60 * 60);
        }

        @Nested
        class Connections {

            @Nested
            class FromLocation {

                @Test
                void shouldGetConnections() {
                    List<Connection> connections = service.getConnections(new GeoCoordinate(36.425288, -117.133162),
                            new GeoCoordinate(36.88108, -116.81797), LocalDateTime.of(2008, 5, 15, 8, 0),
                            TimeType.DEPARTURE, config);
                    assertFalse(connections.isEmpty(), "Expected to find connections.");
                }

                @Test
                void shouldHandleNoNearestTargetStop() {
                    List<Connection> connections = service.getConnections(new GeoCoordinate(0.0, 0.0),
                            new GeoCoordinate(36.88108, -116.81797), LocalDateTime.of(2008, 5, 15, 8, 0),
                            TimeType.DEPARTURE, config);
                    assertTrue(connections.isEmpty(),
                            "Expected no connections to be found when no nearest stop exists.");
                }

                @Test
                void shouldHandleInactiveDate() {
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
                void shouldGetConnections() {
                    List<Connection> connections = service.getConnections(source,
                            new GeoCoordinate(36.88108, -116.81797), LocalDateTime.of(2008, 5, 15, 8, 0),
                            TimeType.DEPARTURE, config);
                    assertFalse(connections.isEmpty(), "Expected to find connections.");
                }

                @Test
                void shouldHandleNoNearestTargetStop() {
                    List<Connection> connections = service.getConnections(source, new GeoCoordinate(-89, 0),
                            LocalDateTime.of(2008, 5, 15, 8, 0), TimeType.DEPARTURE, config);
                    assertTrue(connections.isEmpty(),
                            "Expected no connections to be found when no nearest stop exists.");
                }

                @Test
                void shouldHandleInactiveDate() {
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
                void shouldGetIsolines() {
                    Map<Stop, Connection> connections = service.getIsoLines(new GeoCoordinate(36.425288, -117.133162),
                            LocalDateTime.of(2008, 5, 15, 8, 0), TimeType.DEPARTURE, config);
                    assertFalse(connections.isEmpty(), "Expected to find connections.");
                }

                @Test
                void shouldHandleNoIsolinesFound() {
                    Map<Stop, Connection> connections = service.getIsoLines(new GeoCoordinate(0.0, 0.0),
                            LocalDateTime.of(2008, 5, 15, 8, 0), TimeType.DEPARTURE, config);
                    assertTrue(connections.isEmpty(), "Expected no isolines to be found when no nearest stop exists.");
                }

                @Test
                void shouldHandleInactiveDate() {
                    Map<Stop, Connection> connections = service.getIsoLines(new GeoCoordinate(36.425288, -117.133162),
                            LocalDateTime.of(2023, 5, 15, 8, 0), TimeType.DEPARTURE, config);
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
                void shouldGetIsolines() {
                    Map<Stop, Connection> connections = service.getIsoLines(source, LocalDateTime.of(2008, 5, 15, 8, 0),
                            TimeType.DEPARTURE, config);
                    assertFalse(connections.isEmpty(), "Expected to find connections.");
                }

                @Test
                void shouldHandleInactiveDate() {
                    Map<Stop, Connection> connections = service.getIsoLines(source, LocalDateTime.of(2023, 5, 15, 8, 0),
                            TimeType.DEPARTURE, config);
                    assertTrue(connections.isEmpty(),
                            "Expected no isolines to be found when no active trips exist on the date.");
                }
            }
        }

    }

}