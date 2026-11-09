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
    }

}
