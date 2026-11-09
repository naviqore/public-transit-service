package org.naviqore.benchmark.compare;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVPrinter;
import org.jspecify.annotations.Nullable;
import org.naviqore.benchmark.Arguments;
import org.naviqore.benchmark.BenchmarkUtils;
import org.naviqore.benchmark.Feed;
import org.naviqore.benchmark.version.Version;
import org.naviqore.benchmark.workload.Query;
import org.naviqore.benchmark.workload.Workload;
import org.naviqore.benchmark.workload.Workloads;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Compares the answers of two versions to the queries of workloads, query by query: whether an answer stayed the same,
 * and if not, how it changed. A change that is not meant to change answers, e.g. one for speed, shows here what it
 * changed nonetheless.
 * <p>
 * Every query has one of these outcomes:
 * <ul>
 *     <li>the same answer;</li>
 *     <li>the same answer in another order: equally good connections or stops listed in another order, which also
 *     happens between runs of one version;</li>
 *     <li>a failure fixed, still failing, failing differently, or a new failure;</li>
 *     <li>a different answer, and how it differs. For isolines: whether stops are reached earlier (better), later
 *     (worse) or no longer (lost), or at the same time with a walk replaced by a ride (folded) or by other legs; the
 *     outcome names these changes and the difference counts their stops, so that queries with the same changes share
 *     an outcome. For connections: whether their number changed, and whether the times they optimize, the arrival of
 *     a departure query and the departure of an arrival query, got better or worse.</li>
 * </ul>
 * The outcome of every query goes to a CSV file; the number of queries of each outcome, with examples, to the log.
 */
@UtilityClass
@Slf4j
public class AnswerComparison {

    private static final int EXAMPLES_PER_OUTCOME = 4;
    private static final int MAX_TEXT_LENGTH = 300;
    private static final String FAILURE_PREFIX = "EXCEPTION ";

    public static void run(Arguments arguments) throws Exception {
        Feed feed = arguments.feed();
        Version baseline = arguments.version(0);
        Version candidate = arguments.version(1);
        baseline.build(feed.path());
        candidate.build(feed.path());

        // outcomes in the order of their kind, each with its number of queries and a few examples
        Map<Outcome, Integer> counts = new TreeMap<>();
        Map<Outcome, List<String>> examples = new TreeMap<>();
        try (CSVPrinter csv = BenchmarkUtils.openCsv(arguments.output(), feed.name(), LocalDateTime.now(), "compare",
                "workload", "query", "outcome", "difference")) {
            for (String name : arguments.workloads(List.of(Workloads.ANSWERS))) {
                // the queries are drawn from the baseline, so that a candidate drawing other stops changes nothing
                Workload workload = Workloads.of(name, baseline.subject());
                log.info("Comparing the answers to the {} queries of workload {}", workload.queries().size(), name);
                for (Query query : workload.queries()) {
                    Comparison comparison = compare(query, baseline.answer(query), candidate.answer(query));
                    csv.printRecord(name, query, comparison.outcome(), comparison.difference());
                    counts.merge(comparison.outcome(), 1, Integer::sum);
                    if (!comparison.difference().isEmpty()) {
                        List<String> list = examples.computeIfAbsent(comparison.outcome(), _ -> new ArrayList<>());
                        if (list.size() < EXAMPLES_PER_OUTCOME) {
                            list.add(query + "\n    " + comparison.difference());
                        }
                    }
                }
            }
        }

        log.info("Answers of {} (candidate) compared with those of {} (baseline):", candidate.name(), baseline.name());
        counts.forEach((outcome, count) -> log.info("  {} {}", String.format(Locale.ROOT, "%,6d", count), outcome));
        examples.forEach((outcome, list) -> {
            log.info("Examples of {}:", outcome);
            list.forEach(example -> log.info("  {}", example));
        });
    }

    /**
     * Compares the answers of two versions to a query.
     *
     * @param query  the query
     * @param before the answer of the baseline
     * @param after  the answer of the candidate
     */
    static Comparison compare(Query query, List<String> before, List<String> after) {
        String failureBefore = failure(before);
        String failureAfter = failure(after);

        if (failureBefore == null && failureAfter == null) {
            if (before.equals(after)) {
                return new Comparison(new Outcome(Outcome.Kind.SAME, ""), "");
            }
            if (sorted(before).equals(sorted(after))) {
                return new Comparison(new Outcome(Outcome.Kind.SAME_OTHER_ORDER, ""), "");
            }
            String firstDifference = "before: " + cut(firstMissing(before, after)) + "\n    after:  " + cut(
                    firstMissing(after, before));
            if (query.kind() == Query.Kind.ISOLINES) {
                return compareIsolines(query, before, after, firstDifference);
            }
            return new Comparison(new Outcome(Outcome.Kind.DIFFERENT, compareConnections(query, before, after)),
                    firstDifference);
        }
        if (failureAfter == null) {
            return new Comparison(new Outcome(Outcome.Kind.FIXED, type(failureBefore)),
                    after.size() + " answers instead of " + cut(failureBefore));
        }
        if (failureBefore == null) {
            return new Comparison(new Outcome(Outcome.Kind.NEW_FAILURE, type(failureAfter)), cut(failureAfter));
        }
        if (type(failureBefore).equals(type(failureAfter))) {
            return new Comparison(new Outcome(Outcome.Kind.STILL_FAILING, type(failureAfter)), "");
        }

        return new Comparison(
                new Outcome(Outcome.Kind.FAILING_DIFFERENTLY, type(failureBefore) + " -> " + type(failureAfter)),
                cut(failureAfter));
    }

    private static Comparison compareIsolines(Query query, List<String> before, List<String> after,
                                              String firstDifference) {
        Map<String, Journey> was = byStop(before);
        Map<String, Journey> now = byStop(after);
        boolean arrival = query.timeType() == Query.TimeType.ARRIVAL;

        int folded = 0;
        int better = 0;
        int worse = 0;
        int otherLegs = 0;
        for (Map.Entry<String, Journey> entry : now.entrySet()) {
            Journey previous = was.get(entry.getKey());
            Journey journey = entry.getValue();
            if (previous == null) {
                better++;
                continue;
            }
            int comparison = journey.optimized(arrival).compareTo(previous.optimized(arrival));
            if (comparison != 0) {
                if (improves(comparison, arrival)) {
                    better++;
                } else {
                    worse++;
                }
            } else if (journey.walks() < previous.walks()) {
                folded++;
            } else if (!journey.equals(previous)) {
                otherLegs++;
            }
        }
        int lost = (int) was.keySet().stream().filter(stop -> !now.containsKey(stop)).count();

        // the changes there are name the outcome, their numbers of stops go to the difference
        Map<String, Integer> changes = new LinkedHashMap<>();
        changes.put("better", better);
        changes.put("worse", worse);
        changes.put("lost", lost);
        changes.put("folded", folded);
        changes.put("other legs", otherLegs);
        changes.values().removeIf(count -> count == 0);
        String counts = changes.entrySet()
                .stream()
                .map(change -> change.getKey() + " " + change.getValue())
                .collect(Collectors.joining(", "));

        return new Comparison(
                new Outcome(Outcome.Kind.DIFFERENT, "isolines, stops: " + String.join(", ", changes.keySet())),
                "stops: " + counts + "\n    " + firstDifference);
    }

    private static String compareConnections(Query query, List<String> before, List<String> after) {
        boolean arrival = query.timeType() == Query.TimeType.ARRIVAL;
        Comparator<Journey> byOptimized = Comparator.comparing(journey -> journey.optimized(arrival));
        List<Journey> was = before.stream().map(Journey::of).sorted(byOptimized).toList();
        List<Journey> now = after.stream().map(Journey::of).sorted(byOptimized).toList();

        if (was.size() != now.size()) {
            return "connections, other number";
        }
        boolean anyBetter = false;
        for (int i = 0; i < was.size(); i++) {
            int comparison = now.get(i).optimized(arrival).compareTo(was.get(i).optimized(arrival));
            if (comparison != 0) {
                if (!improves(comparison, arrival)) {
                    return "connections, worse";
                }
                anyBetter = true;
            }
        }
        if (anyBetter) {
            return "connections, better";
        }
        for (int i = 0; i < was.size(); i++) {
            Journey previous = was.get(i);
            Journey journey = now.get(i);
            // the times optimized are the same: the others decide whether the connection got shorter
            boolean noLonger = arrival ? !journey.arrival().isAfter(previous.arrival()) : !journey.departure()
                    .isBefore(previous.departure());
            if (!noLonger || journey.walks() > previous.walks()) {
                return "connections, same times optimized, longer or with more walks";
            }
        }

        return "connections, same times optimized, other legs";
    }

    /**
     * Whether a time optimized improves, given how it compares with the time before: a departure query optimizes the
     * arrival, which improves when it is earlier, an arrival query the departure, which improves when it is later.
     */
    private static boolean improves(int comparison, boolean arrivalQuery) {
        return arrivalQuery ? comparison > 0 : comparison < 0;
    }

    private static Map<String, Journey> byStop(List<String> isolines) {
        Map<String, Journey> journeys = HashMap.newHashMap(isolines.size());
        for (String line : isolines) {
            int space = line.indexOf(' ');
            journeys.put(line.substring(0, space), Journey.of(line.substring(space + 1)));
        }

        return journeys;
    }

    private static @Nullable String failure(List<String> answer) {
        return answer.size() == 1 && answer.getFirst().startsWith(FAILURE_PREFIX) ? answer.getFirst() : null;
    }

    private static String type(String failure) {
        String type = failure.substring(FAILURE_PREFIX.length());
        int colon = type.indexOf(':');
        return colon < 0 ? type : type.substring(0, colon);
    }

    private static List<String> sorted(List<String> lines) {
        List<String> copy = new ArrayList<>(lines);
        Collections.sort(copy);
        return copy;
    }

    private static String firstMissing(List<String> lines, List<String> others) {
        Set<String> other = new HashSet<>(others);
        return lines.stream().filter(line -> !other.contains(line)).findFirst().orElse("<none>");
    }

    private static String cut(String text) {
        return text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) + "…" : text;
    }

    /**
     * The outcome of a query, its kind and how it came about, e.g. which failure was fixed.
     */
    record Outcome(Kind kind, String detail) implements Comparable<Outcome> {

        @Override
        public int compareTo(Outcome other) {
            return Comparator.comparing(Outcome::kind).thenComparing(Outcome::detail).compare(this, other);
        }

        @Override
        public String toString() {
            return detail.isEmpty() ? kind.label : kind.label + ": " + detail;
        }

        enum Kind {
            SAME("same answer"),
            SAME_OTHER_ORDER("same answer, other order"),
            FIXED("fixed"),
            STILL_FAILING("still failing"),
            FAILING_DIFFERENTLY("failing differently"),
            DIFFERENT("different"),
            NEW_FAILURE("new failure");

            private final String label;

            Kind(String label) {
                this.label = label;
            }
        }
    }

    /**
     * The outcome of a query and, unless the answers are the same, how they differ.
     */
    record Comparison(Outcome outcome, String difference) {
    }

    /**
     * A connection, as far as comparisons need it: when it departs and arrives, how many of its legs walk, and its legs
     * as text.
     */
    record Journey(OffsetDateTime departure, OffsetDateTime arrival, int walks, String legs) {

        static Journey of(String connection) {
            int separator = connection.indexOf(" |");
            String[] times = connection.substring(0, separator).split(" -> ");
            String legs = connection.substring(separator + 2);
            int walks = 0;
            for (String leg : legs.split(" ;")) {
                String trimmed = leg.trim();
                if (trimmed.startsWith("TR ") || trimmed.startsWith("WA ")) {
                    walks++;
                }
            }

            return new Journey(OffsetDateTime.parse(times[0]), OffsetDateTime.parse(times[1]), walks, legs);
        }

        /**
         * The time a query optimizes: the arrival of a departure query, the departure of an arrival query.
         */
        Instant optimized(boolean arrivalQuery) {
            return (arrivalQuery ? departure : arrival).toInstant();
        }
    }

}
