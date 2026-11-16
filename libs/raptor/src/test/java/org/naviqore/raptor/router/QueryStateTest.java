package org.naviqore.raptor.router;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.naviqore.raptor.TimeType;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The best times of a query state: over the passes of a range query, the best times of a round are those of the round
 * and the rounds before it, of all passes so far; in a query of one pass, the rounds share them.
 */
class QueryStateTest {

    private static final int A = 0;
    private static final int B = 1;
    private static final int C = 2;

    private static QueryState state(TimeType timeType, boolean bestTimesPerRound) {
        Stop[] stops = new Stop[3];
        for (int i = 0; i < stops.length; i++) {
            stops[i] = new Stop("stop" + i, 0, 0, 0, false, 0, 0, 0, 0);
        }
        return new QueryState(stops, timeType, bestTimesPerRound);
    }

    private static QueryState.Label tripLabel(int stopIdx, int time) {
        return new QueryState.Label(0, time, QueryState.LabelType.ROUTE, 0, 0, stopIdx, null);
    }

    @Nested
    class PerRound {

        @Test
        void roundStartsFromTheBestTimesOfTheRoundBefore() {
            QueryState state = state(TimeType.DEPARTURE, true);
            state.setBestTime(A, 100);
            state.setTripLabel(B, tripLabel(B, 200));

            state.addNewRound();

            assertEquals(100, state.getComparableBestTime(A));
            assertEquals(200, state.getBestTripTime(B));
            assertEquals(QueryState.INFINITY, state.getComparableBestTime(C));
            assertEquals(QueryState.INFINITY, state.getBestTripTime(A));
        }

        @Test
        void roundTakesOverWhatALaterPassImprovedInTheRoundBefore() {
            QueryState state = state(TimeType.DEPARTURE, true);
            state.setBestTime(A, 100);
            state.addNewRound();
            state.setBestTime(B, 300);
            state.setTripLabel(B, tripLabel(B, 300));

            state.startPass();
            state.setBestTime(A, 90);
            state.setBestTime(C, 250);
            state.setTripLabel(C, tripLabel(C, 260));
            state.addNewRound();

            assertEquals(90, state.getComparableBestTime(A));
            assertEquals(300, state.getComparableBestTime(B));
            assertEquals(300, state.getBestTripTime(B));
            assertEquals(250, state.getComparableBestTime(C));
            assertEquals(260, state.getBestTripTime(C));
        }

        @Test
        void roundKeepsItsOwnBetterTimes() {
            QueryState state = state(TimeType.DEPARTURE, true);
            state.setBestTime(A, 100);
            state.addNewRound();
            state.setBestTime(B, 150);

            // a later pass reaches B in round 0, but later than the pass before did in round 1
            state.startPass();
            state.setBestTime(B, 180);
            assertEquals(180, state.getComparableBestTime(B));
            state.addNewRound();

            assertEquals(150, state.getComparableBestTime(B));
        }

        @Test
        void improvementReachesARoundAfterPassesThatEndedBeforeIt() {
            QueryState state = state(TimeType.DEPARTURE, true);
            state.setBestTime(A, 100);
            state.addNewRound();
            state.addNewRound();
            assertEquals(100, state.getComparableBestTime(A));

            // two passes that end in round 0 and 1, each improving A there
            state.startPass();
            state.setBestTime(A, 80);
            state.startPass();
            state.setBestTime(A, 70);
            state.addNewRound();
            state.setTripLabel(A, tripLabel(A, 75));

            // the next pass runs through all rounds
            state.startPass();
            state.addNewRound();
            assertEquals(70, state.getComparableBestTime(A));
            assertEquals(75, state.getBestTripTime(A));
            state.addNewRound();
            assertEquals(70, state.getComparableBestTime(A));
            assertEquals(75, state.getBestTripTime(A));
        }

        @Test
        void arrivalQueryTakesTheLaterTime() {
            QueryState state = state(TimeType.ARRIVAL, true);
            state.setBestTime(A, 100);
            state.addNewRound();
            state.setBestTime(B, 50);

            state.startPass();
            state.setBestTime(A, 120);
            state.setBestTime(B, 40);
            state.addNewRound();

            assertEquals(120, state.getComparableBestTime(A));
            assertEquals(50, state.getComparableBestTime(B));
            assertEquals(-QueryState.INFINITY, state.getComparableBestTime(C));
        }
    }

    @Nested
    class OnePass {

        @Test
        void roundsShareTheBestTimes() {
            QueryState state = state(TimeType.DEPARTURE, false);
            state.setBestTime(A, 100);
            state.addNewRound();
            assertEquals(100, state.getComparableBestTime(A));

            state.setBestTime(A, 90);
            state.setTripLabel(B, tripLabel(B, 200));
            state.addNewRound();

            assertEquals(90, state.getComparableBestTime(A));
            assertEquals(200, state.getBestTripTime(B));
        }
    }
}
