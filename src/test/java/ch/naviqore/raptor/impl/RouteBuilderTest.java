package ch.naviqore.raptor.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RouteBuilderTest {

    private static final String ROUTE_1 = "route1";
    private static final String TRIP_1 = "trip1";
    private static final String STOP_1 = "stop1";
    private static final String STOP_2 = "stop2";
    private static final String STOP_3 = "stop3";
    private RouteBuilder builder;

    @Nested
    class LinearRoute {

        private static final List<String> STOP_IDS = List.of(STOP_1, STOP_2, STOP_3);

        @BeforeEach
        void setUp() {
            builder = new RouteBuilder(ROUTE_1, STOP_IDS);
            builder.addTrip(TRIP_1);
        }

        @Nested
        class AddTrip {

            @Test
            void shouldAddValidTrips() {
                assertDoesNotThrow(() -> builder.addTrip("trip2"));
            }

            @Test
            void shouldNotAddDuplicateTrip() {
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                        () -> builder.addTrip(TRIP_1));
                assertEquals("Trip trip1 already exists.", exception.getMessage());
            }
        }

        @Nested
        class AddStopTime {

            @Test
            void shouldAddValidStopTimes() {
                StopTime stopTime1 = new StopTime(100, 200);
                StopTime stopTime2 = new StopTime(300, 400);
                StopTime stopTime3 = new StopTime(500, 600);

                assertDoesNotThrow(() -> builder.addStopTime(TRIP_1, 0, STOP_1, stopTime1));
                assertDoesNotThrow(() -> builder.addStopTime(TRIP_1, 1, STOP_2, stopTime2));
                assertDoesNotThrow(() -> builder.addStopTime(TRIP_1, 2, STOP_3, stopTime3));
            }

            @Test
            void shouldNotAddStopTimeToNonExistentTrip() {
                StopTime stopTime = new StopTime(100, 200);
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                        () -> builder.addStopTime("nonexistentTrip", 0, STOP_1, stopTime));
                assertEquals("Trip nonexistentTrip does not exist.", exception.getMessage());
            }

            @Test
            void shouldNotAddStopTimeWithNegativePosition() {
                StopTime stopTime = new StopTime(100, 200);
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                        () -> builder.addStopTime(TRIP_1, -1, STOP_1, stopTime));
                assertEquals("Position -1 is out of bounds [0, " + STOP_IDS.size() + ").", exception.getMessage());
            }

            @Test
            void shouldNotAddStopTimeWithPositionOutOfBounds() {
                StopTime stopTime = new StopTime(100, 200);
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                        () -> builder.addStopTime(TRIP_1, STOP_IDS.size(), STOP_1, stopTime));
                assertEquals("Position " + STOP_IDS.size() + " is out of bounds [0, " + STOP_IDS.size() + ").",
                        exception.getMessage());
            }

            @Test
            void shouldNotAddStopTimeForNonMatchingStop() {
                StopTime stopTime = new StopTime(100, 200);
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                        () -> builder.addStopTime(TRIP_1, 0, "nonexistentStop", stopTime));
                assertEquals("Stop nonexistentStop does not match stop stop1 at position 0.", exception.getMessage());
                exception = assertThrows(IllegalArgumentException.class,
                        () -> builder.addStopTime(TRIP_1, 0, STOP_2, stopTime));
                assertEquals("Stop stop2 does not match stop stop1 at position 0.", exception.getMessage());
            }

            @Test
            void shouldNotAddDuplicateStopTimes() {
                StopTime stopTime = new StopTime(100, 200);
                builder.addStopTime(TRIP_1, 0, STOP_1, stopTime);
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                        () -> builder.addStopTime(TRIP_1, 0, STOP_1, stopTime));
                assertEquals("Stop time for stop stop1 already exists.", exception.getMessage());
            }

            @Test
            void shouldNotAddStopTimesWithOverlapOnPreviousStop() {
                StopTime stopTime1 = new StopTime(100, 200);
                StopTime stopTime2 = new StopTime(150, 250); // overlapping times

                builder.addStopTime(TRIP_1, 0, STOP_1, stopTime1);
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                        () -> builder.addStopTime(TRIP_1, 1, STOP_2, stopTime2));
                assertEquals("Departure time at previous stop is greater than arrival time at current stop.",
                        exception.getMessage());
            }

            @Test
            void shouldNotAddStopTimesWithOverlapOnNextStop() {
                StopTime stopTime1 = new StopTime(100, 200);
                StopTime stopTime2 = new StopTime(250, 350);
                StopTime stopTime3 = new StopTime(300, 400);

                builder.addStopTime(TRIP_1, 0, STOP_1, stopTime1);
                builder.addStopTime(TRIP_1, 2, STOP_3, stopTime3);

                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                        () -> builder.addStopTime(TRIP_1, 1, STOP_2, stopTime2));
                assertEquals("Departure time at current stop is greater than arrival time at next stop.",
                        exception.getMessage());
            }
        }
    }

    @Nested
    class CircularRoute {

        private static final List<String> STOP_IDS = List.of(STOP_1, STOP_2, STOP_3, STOP_1);

        @BeforeEach
        void setUp() {
            builder = new RouteBuilder(ROUTE_1, STOP_IDS);
            builder.addTrip(TRIP_1);
        }

        @Test
        void shouldAddSameStopTwice() {
            StopTime stopTime1 = new StopTime(100, 200);
            StopTime stopTime2 = new StopTime(300, 400);
            StopTime stopTime3 = new StopTime(500, 600);
            StopTime stopTime4 = new StopTime(700, 800);

            builder.addStopTime(TRIP_1, 0, STOP_1, stopTime1);
            builder.addStopTime(TRIP_1, 1, STOP_2, stopTime2);
            builder.addStopTime(TRIP_1, 2, STOP_3, stopTime3);
            builder.addStopTime(TRIP_1, 3, STOP_1, stopTime4);

            assertDoesNotThrow(builder::build);
        }

        @Test
        void shouldAddSameStopTwiceRandomOrder() {
            StopTime stopTime1 = new StopTime(100, 200);
            StopTime stopTime2 = new StopTime(300, 400);
            StopTime stopTime3 = new StopTime(500, 600);
            StopTime stopTime4 = new StopTime(700, 800);

            builder.addStopTime(TRIP_1, 1, STOP_2, stopTime2);
            builder.addStopTime(TRIP_1, 0, STOP_1, stopTime1);
            builder.addStopTime(TRIP_1, 3, STOP_1, stopTime4);
            builder.addStopTime(TRIP_1, 2, STOP_3, stopTime3);

            assertDoesNotThrow(builder::build);
        }

        @Test
        void shouldNotAddStopTimesWithOverlapOnNextStop() {
            StopTime stopTime1 = new StopTime(100, 200);
            StopTime stopTime2 = new StopTime(300, 400);
            StopTime stopTime3 = new StopTime(500, 650);
            StopTime stopTime4 = new StopTime(600, 700);

            builder.addStopTime(TRIP_1, 1, STOP_2, stopTime2);
            builder.addStopTime(TRIP_1, 0, STOP_1, stopTime1);
            builder.addStopTime(TRIP_1, 2, STOP_3, stopTime3);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> builder.addStopTime(TRIP_1, 3, STOP_1, stopTime4));
            assertEquals("Departure time at previous stop is greater than arrival time at current stop.",
                    exception.getMessage());
        }
    }

    @Nested
    class Build {

        private static final List<String> STOP_IDS_1 = List.of(STOP_1, STOP_2, STOP_3);

        @BeforeEach
        void setUp() {
            builder = new RouteBuilder(ROUTE_1, STOP_IDS_1);
            builder.addTrip(TRIP_1);
        }

        @Nested
        class Validate {

            @Test
            void shouldValidateCompleteRoute() {
                StopTime stopTime1 = new StopTime(100, 200);
                StopTime stopTime2 = new StopTime(300, 400);
                StopTime stopTime3 = new StopTime(500, 600);

                builder.addStopTime(TRIP_1, 0, STOP_1, stopTime1);
                builder.addStopTime(TRIP_1, 1, STOP_2, stopTime2);
                builder.addStopTime(TRIP_1, 2, STOP_3, stopTime3);

                assertDoesNotThrow(() -> builder.build());
            }

            @Test
            void shouldNotValidateWhenStopTimeIsMissing() {
                StopTime stopTime1 = new StopTime(100, 200);
                StopTime stopTime2 = new StopTime(300, 400);

                builder.addStopTime(TRIP_1, 0, STOP_1, stopTime1);
                builder.addStopTime(TRIP_1, 1, STOP_2, stopTime2);

                IllegalStateException exception = assertThrows(IllegalStateException.class, () -> builder.build());
                assertTrue(exception.getMessage().contains("Stop time at stop 2 on trip trip1 not set."));
            }
        }

        @Nested
        class MultipleRoutes {

            private static final String ROUTE_2 = "route2";
            private static final String TRIP_2 = "trip2";
            private static final List<String> STOP_IDS_2 = List.of(STOP_3, STOP_2, STOP_1);
            private RouteBuilder builder2;

            @BeforeEach
            void setUp() {
                builder.addTrip(TRIP_2);
                builder2 = new RouteBuilder(ROUTE_2, STOP_IDS_2);
                builder2.addTrip(TRIP_1);
                builder2.addTrip(TRIP_2);
            }

            @Test
            void shouldSortRouteContainersByFirstTripDepartureTime() {
                // add everything in reverse order: latest stops first
                // route1, trip2: third trip departure
                builder.addStopTime(TRIP_2, 2, STOP_3, new StopTime(900, 900));
                builder.addStopTime(TRIP_2, 1, STOP_2, new StopTime(600, 700));
                builder.addStopTime(TRIP_2, 0, STOP_1, new StopTime(400, 500));

                // route2, trip2: fourth trip departure
                builder2.addStopTime(TRIP_2, 2, STOP_1, new StopTime(950, 950));
                builder2.addStopTime(TRIP_2, 1, STOP_2, new StopTime(650, 750));
                builder2.addStopTime(TRIP_2, 0, STOP_3, new StopTime(450, 550));

                // route1, trip1: second trip departure
                builder.addStopTime(TRIP_1, 2, STOP_3, new StopTime(550, 650));
                builder.addStopTime(TRIP_1, 1, STOP_2, new StopTime(350, 450));
                builder.addStopTime(TRIP_1, 0, STOP_1, new StopTime(150, 250));

                // route2, trip1: first trip departure
                builder2.addStopTime(TRIP_1, 2, STOP_1, new StopTime(500, 600));
                builder2.addStopTime(TRIP_1, 1, STOP_2, new StopTime(300, 400));
                builder2.addStopTime(TRIP_1, 0, STOP_3, new StopTime(100, 200));

                // build route containers
                List<RouteBuilder.RouteContainer> containers = new ArrayList<>();
                containers.add(builder.build());
                containers.add(builder2.build());
                containers.sort(Comparator.naturalOrder());

                // check order
                List<String> expectedOrder = List.of(ROUTE_2, ROUTE_1);
                List<String> actualOrder = containers.stream().map(RouteBuilder.RouteContainer::id).toList();
                assertEquals(expectedOrder, actualOrder);
            }
        }
    }
}