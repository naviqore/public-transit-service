package ch.naviqore.service.gtfs.raptor.routing;

import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.raptor.RaptorAlgorithm;
import ch.naviqore.raptor.router.RaptorConfig;
import ch.naviqore.service.*;
import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.service.config.ServiceConfig;
import ch.naviqore.service.exception.ConnectionRoutingException;
import ch.naviqore.service.gtfs.raptor.GtfsRaptorTestSchedule;
import ch.naviqore.service.gtfs.raptor.TypeMapper;
import ch.naviqore.service.gtfs.raptor.convert.GtfsToRaptorConverter;
import ch.naviqore.service.gtfs.raptor.convert.GtfsTripMaskProvider;
import ch.naviqore.service.gtfs.raptor.convert.TransferGenerator;
import ch.naviqore.service.gtfs.raptor.convert.WalkTransferGenerator;
import ch.naviqore.service.walk.BeeLineWalkCalculator;
import ch.naviqore.service.walk.WalkCalculator;
import ch.naviqore.utils.cache.EvictionCache;
import ch.naviqore.utils.spatial.GeoCoordinate;
import ch.naviqore.utils.spatial.index.KDTree;
import ch.naviqore.utils.spatial.index.KDTreeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static ch.naviqore.service.config.ServiceConfig.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RoutingQueryFacadeIT {

    public static final double EPSILON = 1e-6;
    // Walking legs below the default threshold (120 seconds) would be excluded from the results.
    // Set this to 0 to ensure that no walking legs are filtered out based on duration.
    private static final int WALKING_DURATION_MINIMUM = 0;
    // Offset for creating walkable test coordinates
    private static final double LONGITUDE_OFFSET = 0.001;
    private static final LocalDateTime DATE_TIME = LocalDateTime.of(2008, 5, 15, 0, 0);
    private static final ConnectionQueryConfig QUERY_CONFIG = new ConnectionQueryConfig(10 * 60, 2 * 60, 4,
            24 * 60 * 60, false, false, null);
    private static final ServiceConfig SERVICE_CONFIG = new ServiceConfig("NONE", DEFAULT_GTFS_STATIC_UPDATE_CRON,
            DEFAULT_TRANSFER_TIME_SAME_STOP_DEFAULT, DEFAULT_TRANSFER_TIME_BETWEEN_STOPS_MINIMUM,
            DEFAULT_TRANSFER_TIME_ACCESS_EGRESS, DEFAULT_WALKING_SEARCH_RADIUS, DEFAULT_WALKING_CALCULATOR_TYPE,
            DEFAULT_WALKING_SPEED, WALKING_DURATION_MINIMUM, DEFAULT_MAX_DAYS_TO_SCAN, DEFAULT_RAPTOR_RANGE,
            DEFAULT_CACHE_SIZE, DEFAULT_CACHE_EVICTION_STRATEGY);
    private GtfsSchedule schedule;
    private RoutingQueryFacade facade;

    @BeforeEach
    void setUp() {
        // build schedule
        GtfsRaptorTestSchedule builder = new GtfsRaptorTestSchedule();
        schedule = builder.build();

        // setup walk transfer generator
        KDTree<ch.naviqore.gtfs.schedule.model.Stop> spatialStopIndex = new KDTreeBuilder<ch.naviqore.gtfs.schedule.model.Stop>().addLocations(
                schedule.getStops().values()).build();
        WalkCalculator walkCalculator = new BeeLineWalkCalculator(SERVICE_CONFIG.getWalkingSpeed());
        List<TransferGenerator> transferGenerators = List.of(
                new WalkTransferGenerator(walkCalculator, SERVICE_CONFIG.getTransferTimeBetweenStopsMinimum(),
                        SERVICE_CONFIG.getTransferTimeAccessEgress(), SERVICE_CONFIG.getWalkingSearchRadius(),
                        spatialStopIndex));

        // setup cache and trip mask provider
        EvictionCache.Strategy cacheStrategy = EvictionCache.Strategy.valueOf(
                SERVICE_CONFIG.getCacheEvictionStrategy().name());
        GtfsTripMaskProvider tripMaskProvider = new GtfsTripMaskProvider(schedule,
                SERVICE_CONFIG.getCacheServiceDaySize(), cacheStrategy);

        // configure and setup raptor
        RaptorConfig raptorConfig = new RaptorConfig(SERVICE_CONFIG.getRaptorDaysToScan(),
                SERVICE_CONFIG.getRaptorRange(), SERVICE_CONFIG.getTransferTimeSameStopDefault(),
                SERVICE_CONFIG.getCacheServiceDaySize(), cacheStrategy, tripMaskProvider);
        RaptorAlgorithm raptor = new GtfsToRaptorConverter(raptorConfig, schedule, transferGenerators).run();

        // assemble facade
        facade = new RoutingQueryFacade(SERVICE_CONFIG, schedule, spatialStopIndex, walkCalculator, raptor);
    }

    private Stop getStopById(String id) {
        return TypeMapper.map(schedule.getStops().get(id));
    }

    @Nested
    class Connections {

        private Stop sourceStop;
        private GeoCoordinate sourceCoordinate;
        private Stop targetStop;
        private GeoCoordinate targetCoordinate;

        private static void assertPublicTransitLeg(Leg leg) {
            assertThat(leg).isInstanceOf(PublicTransitLeg.class);
            PublicTransitLeg publicTransitLeg = (PublicTransitLeg) leg;

            assertThat(publicTransitLeg.getTrip().getId()).isEqualTo("T2");
            assertThat(publicTransitLeg.getTrip().getRoute().getId()).isEqualTo("R2");

            assertThat(publicTransitLeg.getDeparture().getStop().getId()).isEqualTo("A");
            assertThat(publicTransitLeg.getArrival().getStop().getId()).isEqualTo("C");
        }

        private void assertFirstMileWalk(Leg leg) {
            assertThat(leg).isInstanceOf(Walk.class);
            Walk walk = (Walk) leg;

            assertThat(walk.getStop()).isPresent();
            assertThat(walk.getStop().get().getId()).isEqualTo("A");

            assertThat(walk.getSourceLocation().distanceTo(sourceCoordinate)).isCloseTo(0, within(EPSILON));
            assertThat(walk.getTargetLocation().distanceTo(sourceStop.getCoordinate())).isCloseTo(0, within(EPSILON));
        }

        private void assertLastMileWalk(Leg leg) {
            assertThat(leg).isInstanceOf(Walk.class);
            Walk walk = (Walk) leg;

            assertThat(walk.getStop()).isPresent();
            assertThat(walk.getStop().get().getId()).isEqualTo("C");

            assertThat(walk.getSourceLocation().distanceTo(targetStop.getCoordinate())).isCloseTo(0, within(EPSILON));
            assertThat(walk.getTargetLocation().distanceTo(targetCoordinate)).isCloseTo(0, within(EPSILON));
        }

        @BeforeEach
        void setUp() {
            sourceStop = getStopById("A");
            sourceCoordinate = new GeoCoordinate(sourceStop.getCoordinate().latitude(),
                    sourceStop.getCoordinate().longitude() - LONGITUDE_OFFSET);
            targetStop = getStopById("C");
            targetCoordinate = new GeoCoordinate(targetStop.getCoordinate().latitude(),
                    targetStop.getCoordinate().longitude() + LONGITUDE_OFFSET);
        }

        @Nested
        class StopToStop {

            @Test
            void departure() throws ConnectionRoutingException {
                List<ch.naviqore.service.Connection> connections = facade.queryConnections(DATE_TIME,
                        TimeType.DEPARTURE, QUERY_CONFIG, sourceStop, targetStop);

                assertThat(connections).hasSize(1);
                Connection connection = connections.getFirst();

                // assert departure and arrival time of complete connection, has to be the same day
                assertThat(connection.getDepartureTime()).isEqualTo("2008-05-15T00:02:00");
                assertThat(connection.getArrivalTime()).isEqualTo("2008-05-15T00:05:00");

                List<Leg> legs = connection.getLegs();
                assertThat(legs).hasSize(1);

                assertPublicTransitLeg(legs.getFirst());
            }

            @Test
            void arrival() throws ConnectionRoutingException {
                List<ch.naviqore.service.Connection> connections = facade.queryConnections(DATE_TIME, TimeType.ARRIVAL,
                        QUERY_CONFIG, sourceStop, targetStop);

                assertThat(connections).hasSize(1);
                Connection connection = connections.getFirst();

                // assert departure and arrival time of complete connection, has to be the previous day
                assertThat(connection.getDepartureTime()).isEqualTo("2008-05-14T00:02:00");
                assertThat(connection.getArrivalTime()).isEqualTo("2008-05-14T00:05:00");

                List<Leg> legs = connection.getLegs();
                assertThat(legs).hasSize(1);

                assertPublicTransitLeg(legs.getFirst());
            }

        }

        @Nested
        class StopToGeo {

            @Test
            void departure() throws ConnectionRoutingException {
                List<ch.naviqore.service.Connection> connections = facade.queryConnections(DATE_TIME,
                        TimeType.DEPARTURE, QUERY_CONFIG, sourceStop, targetCoordinate);

                assertThat(connections).hasSize(1);
                Connection connection = connections.getFirst();

                // assert departure and arrival time of complete connection, has to be the same day
                assertThat(connection.getDepartureTime()).isEqualTo("2008-05-15T00:02:00");
                assertThat(connection.getArrivalTime()).isEqualTo("2008-05-15T00:06:58");

                List<Leg> legs = connection.getLegs();
                assertThat(legs).hasSize(2);

                assertPublicTransitLeg(legs.getFirst());
                assertLastMileWalk(legs.get(1));
            }

            @Test
            void arrival() throws ConnectionRoutingException {
                List<ch.naviqore.service.Connection> connections = facade.queryConnections(DATE_TIME, TimeType.ARRIVAL,
                        QUERY_CONFIG, sourceStop, targetCoordinate);

                assertThat(connections).hasSize(1);
                Connection connection = connections.getFirst();

                // assert departure and arrival time of complete connection, has to be the previous day
                assertThat(connection.getDepartureTime()).isEqualTo("2008-05-14T00:02:00");
                assertThat(connection.getArrivalTime()).isEqualTo("2008-05-14T00:06:58");

                List<Leg> legs = connection.getLegs();
                assertThat(legs).hasSize(2);

                assertPublicTransitLeg(legs.getFirst());
                assertLastMileWalk(legs.get(1));
            }
        }

        @Nested
        class GeoToStop {

            @Test
            void departure() throws ConnectionRoutingException {
                List<ch.naviqore.service.Connection> connections = facade.queryConnections(DATE_TIME,
                        TimeType.DEPARTURE, QUERY_CONFIG, sourceCoordinate, targetStop);

                assertThat(connections).hasSize(1);
                Connection connection = connections.getFirst();

                // assert departure and arrival time of complete connection, has to be the same day
                assertThat(connection.getDepartureTime()).isEqualTo("2008-05-15T00:00:02");
                assertThat(connection.getArrivalTime()).isEqualTo("2008-05-15T00:05:00");

                List<Leg> legs = connection.getLegs();
                assertThat(legs).hasSize(2);

                assertFirstMileWalk(legs.getFirst());
                assertPublicTransitLeg(legs.get(1));
            }

            @Test
            void arrival() throws ConnectionRoutingException {
                List<ch.naviqore.service.Connection> connections = facade.queryConnections(DATE_TIME, TimeType.ARRIVAL,
                        QUERY_CONFIG, sourceCoordinate, targetStop);

                assertThat(connections).hasSize(1);
                Connection connection = connections.getFirst();

                // assert departure and arrival time of complete connection, has to be the previous day
                assertThat(connection.getDepartureTime()).isEqualTo("2008-05-14T00:00:02");
                assertThat(connection.getArrivalTime()).isEqualTo("2008-05-14T00:05:00");

                List<Leg> legs = connection.getLegs();
                assertThat(legs).hasSize(2);

                assertFirstMileWalk(legs.getFirst());
                assertPublicTransitLeg(legs.get(1));
            }

        }

        @Nested
        class GeoToGeo {

            @Test
            void departure() throws ConnectionRoutingException {
                List<ch.naviqore.service.Connection> connections = facade.queryConnections(DATE_TIME,
                        TimeType.DEPARTURE, QUERY_CONFIG, sourceCoordinate, targetCoordinate);

                assertThat(connections).hasSize(1);
                Connection connection = connections.getFirst();

                // assert departure and arrival time of complete connection, has to be the same day
                assertThat(connection.getDepartureTime()).isEqualTo("2008-05-15T00:00:02");
                assertThat(connection.getArrivalTime()).isEqualTo("2008-05-15T00:06:58");

                List<Leg> legs = connection.getLegs();
                assertThat(legs).hasSize(3);

                assertFirstMileWalk(legs.getFirst());
                assertPublicTransitLeg(legs.get(1));
                assertLastMileWalk(legs.get(2));
            }

            @Test
            void arrival() throws ConnectionRoutingException {
                List<ch.naviqore.service.Connection> connections = facade.queryConnections(DATE_TIME, TimeType.ARRIVAL,
                        QUERY_CONFIG, sourceCoordinate, targetCoordinate);

                assertThat(connections).hasSize(1);
                Connection connection = connections.getFirst();

                // assert departure and arrival time of complete connection, has to be the previous day
                assertThat(connection.getDepartureTime()).isEqualTo("2008-05-14T00:00:02");
                assertThat(connection.getArrivalTime()).isEqualTo("2008-05-14T00:06:58");

                List<Leg> legs = connection.getLegs();
                assertThat(legs).hasSize(3);

                assertFirstMileWalk(legs.getFirst());
                assertPublicTransitLeg(legs.get(1));
                assertLastMileWalk(legs.get(2));
            }

        }

        @Nested
        class GeoToGeo_Fallback {

            Stop source;
            Stop target;

            @Nested
            class NoWalkableAlternative {

                Stop source;
                Stop target;

                @BeforeEach
                void setUp() {
                    source = getStopById("A");
                    target = getStopById("D");
                }

                @Test
                void departure() throws ConnectionRoutingException {
                    List<ch.naviqore.service.Connection> connections = facade.queryConnections(DATE_TIME,
                            TimeType.DEPARTURE, QUERY_CONFIG, source, target);

                    assertThat(connections).isEmpty();
                }

                @Test
                void arrival() throws ConnectionRoutingException {
                    List<ch.naviqore.service.Connection> connections = facade.queryConnections(DATE_TIME,
                            TimeType.ARRIVAL, QUERY_CONFIG, source, target);

                    assertThat(connections).isEmpty();
                }
            }

            /**
             * No transit line is available at C2 so the StopToStop connection query will use the GeoToGeo as a
             * fallback.
             */
            @Nested
            class WalkableAlternative {

                @BeforeEach
                void setUp() {
                    source = getStopById("A");
                    target = getStopById("C2");
                }

                @Test
                void departure() throws ConnectionRoutingException {
                    List<ch.naviqore.service.Connection> connections = facade.queryConnections(DATE_TIME,
                            TimeType.DEPARTURE, QUERY_CONFIG, sourceCoordinate, targetCoordinate);

                    assertThat(connections).hasSize(1);
                    Connection connection = connections.getFirst();

                    // assert departure and arrival time of complete connection, has to be the same day
                    assertThat(connection.getDepartureTime()).isEqualTo("2008-05-15T00:00:02");
                    assertThat(connection.getArrivalTime()).isEqualTo("2008-05-15T00:06:58");

                    List<Leg> legs = connection.getLegs();
                    assertThat(legs).hasSize(3);

                    assertFirstMileWalk(legs.getFirst());
                    assertPublicTransitLeg(legs.get(1));
                    assertLastMileWalk(legs.get(2));
                }

                @Test
                void arrival() throws ConnectionRoutingException {
                    List<ch.naviqore.service.Connection> connections = facade.queryConnections(DATE_TIME,
                            TimeType.ARRIVAL, QUERY_CONFIG, sourceCoordinate, targetCoordinate);

                    assertThat(connections).hasSize(1);
                    Connection connection = connections.getFirst();

                    // assert departure and arrival time of complete connection, has to be the previous day
                    assertThat(connection.getDepartureTime()).isEqualTo("2008-05-14T00:00:02");
                    assertThat(connection.getArrivalTime()).isEqualTo("2008-05-14T00:06:58");

                    List<Leg> legs = connection.getLegs();
                    assertThat(legs).hasSize(3);

                    assertFirstMileWalk(legs.getFirst());
                    assertPublicTransitLeg(legs.get(1));
                    assertLastMileWalk(legs.get(2));
                }
            }
        }
    }

    @Nested
    class Isolines {

        @Nested
        class StopSource {

            @Test
            void departure() throws ConnectionRoutingException {
                Stop sourceStop = getStopById("A");

                Map<Stop, ch.naviqore.service.Connection> isolines = facade.queryIsolines(DATE_TIME, TimeType.DEPARTURE,
                        QUERY_CONFIG, sourceStop);

                // TODO: asserts
                assertThat(isolines).isEmpty();
            }

            @Test
            void arrival() throws ConnectionRoutingException {
                Stop sourceStop = getStopById("C");

                Map<Stop, ch.naviqore.service.Connection> isolines = facade.queryIsolines(DATE_TIME, TimeType.ARRIVAL,
                        QUERY_CONFIG, sourceStop);

                // TODO: asserts
                assertThat(isolines).isEmpty();
            }

        }

        @Nested
        class GeoSource {

            @Test
            void departure() throws ConnectionRoutingException {
                Stop sourceStop = getStopById("A");
                GeoCoordinate sourceCoordinate = new GeoCoordinate(sourceStop.getCoordinate().latitude(),
                        sourceStop.getCoordinate().longitude() - LONGITUDE_OFFSET);

                Map<Stop, ch.naviqore.service.Connection> isolines = facade.queryIsolines(DATE_TIME, TimeType.DEPARTURE,
                        QUERY_CONFIG, sourceCoordinate);

                // TODO: asserts
                assertThat(isolines).isEmpty();
            }

            @Test
            void arrival() throws ConnectionRoutingException {
                Stop sourceStop = getStopById("C");
                GeoCoordinate sourceCoordinate = new GeoCoordinate(sourceStop.getCoordinate().latitude(),
                        sourceStop.getCoordinate().longitude() - LONGITUDE_OFFSET);

                Map<Stop, ch.naviqore.service.Connection> isolines = facade.queryIsolines(DATE_TIME, TimeType.ARRIVAL,
                        QUERY_CONFIG, sourceCoordinate);

                // TODO: asserts
                assertThat(isolines).isEmpty();
            }

        }

        @Nested
        class GeoSource_Fallback {

            @Nested
            class WithoutWalkableAlternative {

                @Test
                void departure() throws ConnectionRoutingException {
                    Map<Stop, ch.naviqore.service.Connection> isolines = facade.queryIsolines(DATE_TIME,
                            TimeType.DEPARTURE, QUERY_CONFIG, getStopById("D"));

                    assertThat(isolines).isEmpty();
                }

                @Test
                void arrival() throws ConnectionRoutingException {
                    Map<Stop, ch.naviqore.service.Connection> isolines = facade.queryIsolines(DATE_TIME,
                            TimeType.ARRIVAL, QUERY_CONFIG, getStopById("D"));

                    assertThat(isolines).isEmpty();
                }

            }

            @Nested
            class WalkableAlternative {

                @Test
                void departure() throws ConnectionRoutingException {
                    Map<Stop, ch.naviqore.service.Connection> isolines = facade.queryIsolines(DATE_TIME,
                            TimeType.DEPARTURE, QUERY_CONFIG, getStopById("C2"));

                    // Expected behavior: Since no departures from stops C, C1 and C2, the result must be empty.
                    assertThat(isolines).isEmpty();
                }

                @Test
                void arrival() throws ConnectionRoutingException {
                    Map<Stop, ch.naviqore.service.Connection> isolines = facade.queryIsolines(DATE_TIME,
                            TimeType.ARRIVAL, QUERY_CONFIG, getStopById("C2"));

                    assertThat(isolines).isNotEmpty();
                    // TODO: asserts
                }
            }
        }
    }
}