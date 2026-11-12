package org.naviqore.benchmark.compare;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.naviqore.benchmark.workload.Query;
import org.naviqore.benchmark.workload.Query.Location;
import org.naviqore.benchmark.workload.Query.TimeType;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnswerComparisonTest {

    private static final OffsetDateTime TIME = OffsetDateTime.parse("2026-01-14T08:00+01:00");
    private static final Query CONNECTIONS = Query.connections(Location.stop("A"), Location.stop("B"), TIME,
            TimeType.DEPARTURE, "default");
    private static final Query ISOLINES = Query.isolines(Location.stop("A"), TIME, TimeType.DEPARTURE, "default");

    private static String connection(String departure, String arrival, String... legs) {
        StringBuilder text = new StringBuilder(
                "2026-01-14T" + departure + "+01:00 -> 2026-01-14T" + arrival + "+01:00 |");
        for (String leg : legs) {
            text.append(' ').append(leg).append(" ;");
        }
        return text.toString();
    }

    private static String outcome(Query query, List<String> before, List<String> after) {
        return AnswerComparison.compare(query, before, after).outcome().toString();
    }

    @Nested
    class Connections {

        private final String ride = connection("08:05", "08:30", "PT T1 R1 A -> B");
        private final String fasterRide = connection("08:05", "08:25", "PT T2 R1 A -> B");
        private final String walkAndRide = connection("08:05", "08:30", "WA A", "PT T3 R2 A -> B");

        @Test
        void sameAnswer() {
            assertEquals("same answer", outcome(CONNECTIONS, List.of(ride, fasterRide), List.of(ride, fasterRide)));
        }

        @Test
        void sameAnswerInAnotherOrder() {
            assertEquals("same answer, other order",
                    outcome(CONNECTIONS, List.of(ride, fasterRide), List.of(fasterRide, ride)));
        }

        @Test
        void earlierArrivalIsBetter() {
            assertEquals("different: connections, better", outcome(CONNECTIONS, List.of(ride), List.of(fasterRide)));
        }

        @Test
        void laterArrivalIsWorse() {
            assertEquals("different: connections, worse", outcome(CONNECTIONS, List.of(fasterRide), List.of(ride)));
        }

        @Test
        void anotherNumberOfConnections() {
            assertEquals("different: connections, other number",
                    outcome(CONNECTIONS, List.of(ride), List.of(ride, fasterRide)));
        }

        @Test
        void moreWalksAtTheSameTimes() {
            assertEquals("different: connections, same times optimized, longer or with more walks",
                    outcome(CONNECTIONS, List.of(ride), List.of(walkAndRide)));
        }

        @Test
        void leavingLaterForTheSameArrivalIsShorter() {
            String laterRide = connection("08:10", "08:30", "PT T4 R3 A -> B");

            assertEquals("different: connections, same times optimized, shorter",
                    outcome(CONNECTIONS, List.of(ride), List.of(laterRide)));
            assertEquals("different: connections, same times optimized, longer or with more walks",
                    outcome(CONNECTIONS, List.of(laterRide), List.of(ride)));
        }

        @Test
        void failures() {
            List<String> failure = List.of("EXCEPTION StopNotFoundException: Stop A not found");
            List<String> otherFailure = List.of("EXCEPTION IllegalStateException: broken");

            assertEquals("fixed: StopNotFoundException", outcome(CONNECTIONS, failure, List.of(ride)));
            assertEquals("new failure: StopNotFoundException", outcome(CONNECTIONS, List.of(ride), failure));
            assertEquals("still failing: StopNotFoundException", outcome(CONNECTIONS, failure, failure));
            assertEquals("failing differently: StopNotFoundException -> IllegalStateException",
                    outcome(CONNECTIONS, failure, otherFailure));
        }
    }

    @Nested
    class ConnectionsOverATimeWindow {

        private static final Query WINDOW = Query.connections(Location.stop("A"), Location.stop("B"), TIME,
                TimeType.DEPARTURE, "window60");

        private final String direct = connection("08:10", "09:00", "PT T1 R1 A -> B");
        private final String withTransfer = connection("08:10", "08:40", "PT T2 R2 A -> C", "PT T3 R3 C -> B");
        private final String laterDirect = connection("08:20", "09:00", "PT T4 R1 A -> B");

        private static String outcome(List<String> before, List<String> after) {
            return AnswerComparisonTest.outcome(WINDOW, before, after);
        }

        @Test
        void aConnectionBeatingOneOfTheBaselineIsBetter() {
            assertEquals("different: connections over a time window: better",
                    outcome(List.of(direct, withTransfer), List.of(laterDirect, withTransfer)));
        }

        @Test
        void connectionsTheOtherVersionNeitherHasNorBeatsAreNewOrMissing() {
            assertEquals("different: connections over a time window: new",
                    outcome(List.of(direct), List.of(direct, withTransfer)));
            assertEquals("different: connections over a time window: missing",
                    outcome(List.of(direct, withTransfer), List.of(direct)));
        }

        @Test
        void aConnectionBeatenByTheBaselineOrByItsOwnIsWorse() {
            assertEquals("different: connections over a time window: missing, worse",
                    outcome(List.of(laterDirect), List.of(direct)));
            assertEquals("different: connections over a time window: better, beaten",
                    outcome(List.of(direct), List.of(direct, laterDirect)));
        }

        @Test
        void aConnectionDepartingAfterTheWindowIsOutsideOfIt() {
            String afterTheWindow = connection("09:00", "09:40", "PT T5 R1 A -> B");

            assertEquals("different: connections over a time window: new, outside window",
                    outcome(List.of(direct), List.of(direct, afterTheWindow)));
        }

        @Test
        void theSameTimesOnOtherLegs() {
            assertEquals("different: connections over a time window: other legs",
                    outcome(List.of(direct), List.of(connection("08:10", "09:00", "PT T6 R4 A -> B"))));
        }
    }

    @Nested
    class Isolines {

        private final String stopB = "B " + connection("08:05", "08:30", "PT T1 R1 A -> B");
        private final String stopC = "C " + connection("08:05", "08:40", "PT T1 R1 A -> C");

        @Test
        void namesTheChangesAndCountsTheirStops() {
            List<String> before = List.of(stopB, stopC,
                    "D " + connection("08:05", "08:50", "PT T1 R1 A -> X", "TR X -> D"),
                    "E " + connection("08:05", "08:55", "PT T1 R1 A -> E"),
                    "F " + connection("08:05", "09:00", "PT T1 R1 A -> F"));
            List<String> after = List.of("B " + connection("08:05", "08:25", "PT T2 R1 A -> B"),
                    "C " + connection("08:05", "08:45", "PT T1 R1 A -> C"),
                    "D " + connection("08:05", "08:50", "PT T3 R3 A -> D"),
                    "E " + connection("08:05", "08:55", "PT T4 R4 A -> E"));
            AnswerComparison.Comparison comparison = AnswerComparison.compare(ISOLINES, before, after);

            assertEquals("different: isolines, stops: better, worse, lost, folded, other legs",
                    comparison.outcome().toString());
            assertEquals("stops: better 1, worse 1, lost 1, folded 1, other legs 1",
                    comparison.difference().lines().findFirst().orElseThrow());
        }

        @Test
        void queriesWithTheSameChangesShareAnOutcome() {
            List<String> oneStopOnOtherLegs = List.of("B " + connection("08:05", "08:30", "PT T2 R1 A -> B"), stopC);
            List<String> twoStopsOnOtherLegs = List.of("B " + connection("08:05", "08:30", "PT T2 R1 A -> B"),
                    "C " + connection("08:05", "08:40", "PT T2 R1 A -> C"));

            assertEquals("different: isolines, stops: other legs",
                    outcome(ISOLINES, List.of(stopB, stopC), oneStopOnOtherLegs));
            assertEquals("different: isolines, stops: other legs",
                    outcome(ISOLINES, List.of(stopB, stopC), twoStopsOnOtherLegs));
        }

        @Test
        void aLaterDepartureIsBetterForAnArrivalQuery() {
            Query arriving = Query.isolines(Location.stop("A"), TIME, TimeType.ARRIVAL, "default");
            List<String> before = List.of("B " + connection("07:30", "08:00", "PT T1 R1 B -> A"));
            List<String> after = List.of("B " + connection("07:35", "08:00", "PT T2 R1 B -> A"));

            assertEquals("different: isolines, stops: better", outcome(arriving, before, after));
        }

        @Test
        void overATimeWindow_aShorterTravelTimeIsBetter() {
            Query window = Query.isolines(Location.stop("A"), TIME, TimeType.DEPARTURE, "window60");
            // arriving later after leaving even later: the travel time drops from 25 to 20 minutes
            List<String> after = List.of("B " + connection("08:15", "08:35", "PT T2 R1 A -> B"));

            assertEquals("different: isolines, stops: better", outcome(window, List.of(stopB), after));
            assertEquals("different: isolines, stops: worse", outcome(ISOLINES, List.of(stopB), after));
        }
    }

}
