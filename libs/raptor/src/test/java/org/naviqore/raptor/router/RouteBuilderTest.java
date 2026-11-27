package org.naviqore.raptor.router;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RouteBuilderTest {

    private static final ZoneId ZONE_ID = ZoneId.of("UTC");

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
            builder = new RouteBuilder(ROUTE_1, ZONE_ID, STOP_IDS);
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
            builder = new RouteBuilder(ROUTE_1, ZONE_ID, STOP_IDS);
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
            builder = new RouteBuilder(ROUTE_1, ZONE_ID, STOP_IDS_1);
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
                builder2 = new RouteBuilder(ROUTE_2, ZONE_ID, STOP_IDS_2);
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
                containers.addAll(builder.build());
                containers.addAll(builder2.build());
                containers.sort(Comparator.naturalOrder());

                // check order
                List<String> expectedOrder = List.of(ROUTE_2, ROUTE_1);
                List<String> actualOrder = containers.stream().map(RouteBuilder.RouteContainer::id).toList();
                assertEquals(expectedOrder, actualOrder);
            }
        }

        @Nested
        class OvertakingTrips {

            private static final String TRIP_2 = "trip2";
            private static final String TRIP_3 = "trip3";

            @BeforeEach
            void setUp() {
                builder.addTrip(TRIP_2);
            }

            private void addStopTimes(String tripId, int... times) {
                for (int position = 0; position < times.length; position++) {
                    builder.addStopTime(tripId, position, STOP_IDS_1.get(position),
                            new StopTime(times[position], times[position]));
                }
            }

            private List<List<String>> buildTripIdsPerContainer() {
                List<RouteBuilder.RouteContainer> containers = builder.build();
                assertTrue(containers.stream().allMatch(container -> container.id().equals(ROUTE_1)),
                        "Route containers should keep the id of the route");
                return containers.stream().map(container -> List.copyOf(container.trips().keySet())).toList();
            }

            @Test
            void shouldHoldTripsInOneContainerIfInOrder() {
                addStopTimes(TRIP_2, 200, 400, 600);
                addStopTimes(TRIP_1, 100, 300, 500);

                assertEquals(List.of(List.of(TRIP_1, TRIP_2)), buildTripIdsPerContainer());
            }

            @Test
            void shouldHoldOvertakingTripInSeparateContainer() {
                // trip 2 departs after trip 1, but arrives earlier at the following stops
                addStopTimes(TRIP_1, 100, 300, 500);
                addStopTimes(TRIP_2, 200, 250, 400);

                assertEquals(List.of(List.of(TRIP_1), List.of(TRIP_2)), buildTripIdsPerContainer());
            }

            @Test
            void shouldAddTripToFirstContainerItDoesNotOvertake() {
                // trip 2 overtakes trip 1, trip 3 overtakes trip 1 but not trip 2
                builder.addTrip(TRIP_3);
                addStopTimes(TRIP_1, 100, 300, 500);
                addStopTimes(TRIP_2, 200, 250, 450);
                addStopTimes(TRIP_3, 300, 320, 460);

                assertEquals(List.of(List.of(TRIP_1), List.of(TRIP_2, TRIP_3)), buildTripIdsPerContainer());
            }

            @Test
            void shouldSortFasterOfTripsDepartingTogetherFirst() {
                // both trips depart at the same time, trip 2 is faster and does not count as overtaking
                addStopTimes(TRIP_1, 100, 300, 500);
                addStopTimes(TRIP_2, 100, 250, 450);

                assertEquals(List.of(List.of(TRIP_2, TRIP_1)), buildTripIdsPerContainer());
            }
        }
    }
}