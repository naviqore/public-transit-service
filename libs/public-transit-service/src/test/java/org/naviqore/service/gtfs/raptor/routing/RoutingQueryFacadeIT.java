package org.naviqore.service.gtfs.raptor.routing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.naviqore.gtfs.schedule.model.GtfsSchedule;
import org.naviqore.raptor.RaptorAlgorithm;
import org.naviqore.raptor.router.RaptorConfig;
import org.naviqore.service.*;
import org.naviqore.service.config.ConnectionQueryConfig;
import org.naviqore.service.config.ServiceConfig;
import org.naviqore.service.exception.ConnectionRoutingException;
import org.naviqore.service.gtfs.raptor.GtfsRaptorTestSchedule;
import org.naviqore.service.gtfs.raptor.TypeMapper;
import org.naviqore.service.gtfs.raptor.convert.GtfsToRaptorConverter;
import org.naviqore.service.gtfs.raptor.convert.GtfsTripMaskProvider;
import org.naviqore.service.gtfs.raptor.convert.TransferGenerator;
import org.naviqore.service.gtfs.raptor.convert.WalkTransferGenerator;
import org.naviqore.service.walk.BeeLineWalkCalculator;
import org.naviqore.service.walk.WalkCalculator;
import org.naviqore.utils.cache.EvictionCache;
import org.naviqore.utils.spatial.GeoCoordinate;
import org.naviqore.utils.spatial.index.KDTree;
import org.naviqore.utils.spatial.index.KDTreeBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.naviqore.service.config.ServiceConfig.*;

class RoutingQueryFacadeIT {

    public static final double EPSILON = 1e-6;
    // Walking legs below the default threshold (120 seconds) would be excluded from the results.
    // Set this to 0 to ensure that no walking legs are filtered out based on duration.
    private static final int WALKING_DURATION_MINIMUM = 0;
    // Offset for creating walkable test coordinates
    private static final double LONGITUDE_OFFSET = 0.001;
    private static final LocalDateTime DATE_TIME = LocalDateTime.of(2008, 5, 15, 0, 0);
    private static final ConnectionQueryConfig QUERY_CONFIG = ConnectionQueryConfig.builder()
            .maximumWalkingDuration(10 * 60)
            .minimumTransferDuration(2 * 60)
            .maximumTransferNumber(4)
            .maximumTravelTime(24 * 60 * 60)
            .wheelchairAccessible(false)
            .bikeAllowed(false)
            .build();
    private static final ServiceConfig SERVICE_CONFIG = new ServiceConfig("NONE", DEFAULT_GTFS_STATIC_UPDATE_CRON,
            DEFAULT_TRANSFER_TIME_SAME_STOP_DEFAULT, DEFAULT_TRANSFER_TIME_BETWEEN_STOPS_MINIMUM,
            DEFAULT_TRANSFER_TIME_ACCESS_EGRESS, DEFAULT_WALKING_SEARCH_RADIUS, DEFAULT_WALKING_CALCULATOR_TYPE,
            DEFAULT_WALKING_SPEED, WALKING_DURATION_MINIMUM, DEFAULT_MAX_DAYS_TO_SCAN, DEFAULT_RAPTOR_RANGE,
            DEFAULT_CACHE_SIZE, DEFAULT_CACHE_EVICTION_STRATEGY);
    private GtfsSchedule schedule;
    private RoutingQueryFacade facade;

    private Stop sourceStop;
    private GeoCoordinate sourceCoordinate;
    private Stop targetStop;
    private GeoCoordinate targetCoordinate;

    @BeforeEach
    void setUp() {
        // build schedule
        GtfsRaptorTestSchedule builder = new GtfsRaptorTestSchedule();
        schedule = builder.build();

        // setup walk transfer generator
        KDTree<org.naviqore.gtfs.schedule.model.Stop> spatialStopIndex = new KDTreeBuilder<org.naviqore.gtfs.schedule.model.Stop>().addLocations(
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

        // setup stops and locations for connection and isoline queries
        sourceStop = getStopById("A");
        sourceCoordinate = new GeoCoordinate(sourceStop.getCoordinate().latitude(),
                sourceStop.getCoordinate().longitude() - LONGITUDE_OFFSET);
        targetStop = getStopById("C");
        targetCoordinate = new GeoCoordinate(targetStop.getCoordinate().latitude(),
                targetStop.getCoordinate().longitude() + LONGITUDE_OFFSET);
    }

    private Stop getStopById(String id) {
        return TypeMapper.map(schedule.getStops().get(id));
    }

    private void assertFirstMileWalk(Leg leg) {
        assertFirstMileWalk(leg, sourceStop, sourceCoordinate);
    }

    private void assertFirstMileWalk(Leg leg, Stop stop, GeoCoordinate coordinate) {
        assertThat(leg).isInstanceOf(Walk.class);
        Walk walk = (Walk) leg;

        assertThat(walk.getStop()).isPresent();
        assertThat(walk.getStop().get().getId()).isEqualTo(stop.getId());

        assertThat(walk.getSourceLocation().distanceTo(coordinate)).isCloseTo(0, within(EPSILON));
        assertThat(walk.getTargetLocation().distanceTo(stop.getCoordinate())).isCloseTo(0, within(EPSILON));
    }

    @Nested
    class Connections {

        private static void assertPublicTransitLeg(Leg leg) {
            assertPublicTransitLeg(leg, "A", "C", "T2", "R2");
        }

        private static void assertPublicTransitLeg(Leg leg, String departureStopId, String arrivalStopId, String tripId,
                                                   String routeId) {
            assertThat(leg).isInstanceOf(PublicTransitLeg.class);
            PublicTransitLeg publicTransitLeg = (PublicTransitLeg) leg;

            assertThat(publicTransitLeg.getTrip().getId()).isEqualTo(tripId);
            assertThat(publicTransitLeg.getTrip().getRoute().getId()).isEqualTo(routeId);

            assertThat(publicTransitLeg.getDeparture().getStop().getId()).isEqualTo(departureStopId);
            assertThat(publicTransitLeg.getArrival().getStop().getId()).isEqualTo(arrivalStopId);
        }

        private void assertLastMileWalk(Leg leg) {
            assertLastMileWalk(leg, targetStop, targetCoordinate);
        }

        private void assertLastMileWalk(Leg leg, Stop stop, GeoCoordinate coordinate) {
            assertThat(leg).isInstanceOf(Walk.class);
            Walk walk = (Walk) leg;

            assertThat(walk.getStop()).isPresent();
            assertThat(walk.getStop().get().getId()).isEqualTo(stop.getId());

            assertThat(walk.getSourceLocation().distanceTo(stop.getCoordinate())).isCloseTo(0, within(EPSILON));
            assertThat(walk.getTargetLocation().distanceTo(coordinate)).isCloseTo(0, within(EPSILON));
        }

        @Nested
        class StopToStop {

            @Test
            void departure() throws ConnectionRoutingException {
                List<org.naviqore.service.Connection> connections = facade.queryConnections(DATE_TIME,
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
                List<org.naviqore.service.Connection> connections = facade.queryConnections(DATE_TIME, TimeType.ARRIVAL,
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
                List<org.naviqore.service.Connection> connections = facade.queryConnections(DATE_TIME,
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
                List<org.naviqore.service.Connection> connections = facade.queryConnections(DATE_TIME, TimeType.ARRIVAL,
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

            @Test
            void departure_targetOutOfRange() throws ConnectionRoutingException {
                // this is 440 m from C1 and 560 m from C away, which makes C1 in range of location search and C outside
                // (range search looks within 500 m --> ServiceConfig.DEFAULT_WALKING_SEARCH_RADIUS)
                GeoCoordinate targetCoordinate = new GeoCoordinate(0.005, 2.0);

                // routing from B2 only connects to C and not C1 --> no connection possible
                List<org.naviqore.service.Connection> connections = facade.queryConnections(DATE_TIME,
                        TimeType.DEPARTURE, QUERY_CONFIG, getStopById("B2"), targetCoordinate);
                assertThat(connections).hasSize(0);

                // ensuring that an arrival at C1 allows reaching the target location --> routing B1 -> target
                // (through C1)
                connections = facade.queryConnections(DATE_TIME, TimeType.ARRIVAL, QUERY_CONFIG, getStopById("B1"),
                        targetCoordinate);
                assertThat(connections).hasSize(1);
                Connection connection = connections.getFirst();
                assertThat(connection.getLegs()).hasSize(2);

                assertPublicTransitLeg(connection.getLegs().getFirst(), "B1", "C1", "T1", "R1");
                assertLastMileWalk(connection.getLegs().getLast(), getStopById("C1"), targetCoordinate);
            }
        }

        @Nested
        class GeoToStop {

            @Test
            void departure() throws ConnectionRoutingException {
                List<org.naviqore.service.Connection> connections = facade.queryConnections(DATE_TIME,
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
                List<org.naviqore.service.Connection> connections = facade.queryConnections(DATE_TIME, TimeType.ARRIVAL,
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

            @Test
            void departure_sourceOutOfRange() throws ConnectionRoutingException {
                // this is 440 m from B1 and 670 m from B2 away, which makes B1 in range of location search and B2
                // outside (range search looks within 500 m --> ServiceConfig.DEFAULT_WALKING_SEARCH_RADIUS)
                GeoCoordinate sourceCoordinate = new GeoCoordinate(0.005, 1.0);

                // since walking to stop B1 will take ~5 minutes and this will miss the only trip of the day the default
                // start time is set back by 5 minutes.
                LocalDateTime startTime = DATE_TIME.minusMinutes(5);

                // from source coordinate only departures from stop B1 (i.e. Route 1 going to D1) should be usable,
                // routes departing from same stop complex at B2 (i.e. Route 2 going to D2) should be unusable.
                List<Connection> connections = facade.queryConnections(startTime, TimeType.DEPARTURE, QUERY_CONFIG,
                        sourceCoordinate, getStopById("D1"));
                assertThat(connections).hasSize(1);
                Connection connection = connections.getFirst();
                assertThat(connection.getLegs()).hasSize(2);

                assertFirstMileWalk(connection.getLegs().getFirst(), getStopById("B1"), sourceCoordinate);
                assertPublicTransitLeg(connection.getLegs().getLast(), "B1", "D1", "T1", "R1");

                // when the original request to route to D2 fails, it will fall back to GeoToGeo coordinate routing,
                // however since D1 and D2 are more than 500 m apart this will also fail.
                connections = facade.queryConnections(startTime, TimeType.DEPARTURE, QUERY_CONFIG, sourceCoordinate,
                        getStopById("D2"));
                assertThat(connections).hasSize(0);
            }

        }

        @Nested
        class GeoToGeo {

            @Test
            void departure() throws ConnectionRoutingException {
                List<org.naviqore.service.Connection> connections = facade.queryConnections(DATE_TIME,
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
                List<org.naviqore.service.Connection> connections = facade.queryConnections(DATE_TIME, TimeType.ARRIVAL,
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
                    target = getStopById("E");
                }

                @Test
                void departure() throws ConnectionRoutingException {
                    List<org.naviqore.service.Connection> connections = facade.queryConnections(DATE_TIME,
                            TimeType.DEPARTURE, QUERY_CONFIG, source, target);

                    assertThat(connections).isEmpty();
                }

                @Test
                void arrival() throws ConnectionRoutingException {
                    List<org.naviqore.service.Connection> connections = facade.queryConnections(DATE_TIME,
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
                    List<org.naviqore.service.Connection> connections = facade.queryConnections(DATE_TIME,
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
                    List<org.naviqore.service.Connection> connections = facade.queryConnections(DATE_TIME,
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
                Map<Stop, org.naviqore.service.Connection> isolines = facade.queryIsolines(DATE_TIME,
                        TimeType.DEPARTURE, QUERY_CONFIG, sourceStop);

                assertThat(isolines).hasSize(6);

                for (Connection isoline : isolines.values()) {
                    assertThat(isoline.getLegs()).hasSize(1);

                    // assert departure time of complete connection, has to be the same day
                    assertThat(isoline.getDepartureTime()).isEqualTo("2008-05-15T00:02:00");
                }
            }

            @Test
            void arrival() throws ConnectionRoutingException {
                Map<Stop, org.naviqore.service.Connection> isolines = facade.queryIsolines(DATE_TIME, TimeType.ARRIVAL,
                        QUERY_CONFIG, targetStop);

                assertThat(isolines).hasSize(3);

                for (Connection isoline : isolines.values()) {
                    assertThat(isoline.getLegs()).hasSize(1);

                    // assert arrival time of complete connection, has to be the previous day
                    assertThat(isoline.getArrivalTime().toLocalDate()).isEqualTo("2008-05-14");
                }
            }

        }

        @Nested
        class GeoSource {

            @Test
            void departure() throws ConnectionRoutingException {
                Map<Stop, org.naviqore.service.Connection> isolines = facade.queryIsolines(DATE_TIME,
                        TimeType.DEPARTURE, QUERY_CONFIG, sourceCoordinate);

                assertThat(isolines).hasSize(6);

                for (Connection isoline : isolines.values()) {
                    assertThat(isoline.getLegs()).hasSize(2);

                    // assert departure time of complete connection, has to be the same day
                    assertThat(isoline.getDepartureTime()).isEqualTo("2008-05-15T00:00:02");

                    assertFirstMileWalk(isoline.getLegs().getFirst());
                }
            }

            @Test
            void arrival() throws ConnectionRoutingException {
                Map<Stop, org.naviqore.service.Connection> isolines = facade.queryIsolines(DATE_TIME, TimeType.ARRIVAL,
                        QUERY_CONFIG, targetCoordinate);

                assertThat(isolines).hasSize(3);

                for (Connection isoline : isolines.values()) {
                    assertThat(isoline.getLegs()).hasSize(2);

                    // assert arrival time of complete connection, has to be the previous day
                    assertThat(isoline.getArrivalTime().toLocalDate()).isEqualTo("2008-05-14");

                    // last mile walking leg
                    Leg leg = isoline.getLegs().getLast();
                    assertThat(leg).isInstanceOf(Walk.class);
                    Walk walk = (Walk) leg;

                    assertThat(walk.getStop()).isPresent();
                    assertThat(walk.getStop().get().getId()).isIn("C", "C1");
                    assertThat(walk.getTargetLocation().distanceTo(targetCoordinate)).isCloseTo(0, within(EPSILON));
                }
            }

        }

        @Nested
        class GeoSource_Fallback {

            @Nested
            class WithoutWalkableAlternative {

                @Test
                void departure() throws ConnectionRoutingException {
                    Map<Stop, org.naviqore.service.Connection> isolines = facade.queryIsolines(DATE_TIME,
                            TimeType.DEPARTURE, QUERY_CONFIG, getStopById("D"));

                    assertThat(isolines).isEmpty();
                }

                @Test
                void arrival() throws ConnectionRoutingException {
                    Map<Stop, org.naviqore.service.Connection> isolines = facade.queryIsolines(DATE_TIME,
                            TimeType.ARRIVAL, QUERY_CONFIG, getStopById("E"));

                    assertThat(isolines).isEmpty();
                }

            }

            @Nested
            class WalkableAlternative {

                @Test
                void departure() throws ConnectionRoutingException {
                    Map<Stop, org.naviqore.service.Connection> isolines = facade.queryIsolines(DATE_TIME,
                            TimeType.DEPARTURE, QUERY_CONFIG, getStopById("D"));

                    // Expected behavior: Since no departures from stops D, D1 and D2, the result must be empty.
                    assertThat(isolines).isEmpty();
                }

                @Test
                void arrival() throws ConnectionRoutingException {
                    Stop source = getStopById("C2");
                    Map<Stop, org.naviqore.service.Connection> isolines = facade.queryIsolines(DATE_TIME,
                            TimeType.ARRIVAL, QUERY_CONFIG, source);

                    assertThat(isolines).hasSize(3);

                    for (Connection isoline : isolines.values()) {
                        assertThat(isoline.getLegs()).hasSize(2);

                        // assert arrival time of complete connection, has to be the previous day
                        assertThat(isoline.getArrivalTime().toLocalDate()).isEqualTo("2008-05-14");

                        // last mile walking leg
                        Leg leg = isoline.getLegs().getLast();
                        assertThat(leg).isInstanceOf(Walk.class);
                        Walk walk = (Walk) leg;

                        assertThat(walk.getStop()).isPresent();
                        assertThat(walk.getStop().get().getId()).isIn("C", "C1");
                        assertThat(walk.getTargetLocation().distanceTo(source.getCoordinate())).isCloseTo(0,
                                within(EPSILON));
                    }
                }
            }
        }
    }
}