package org.naviqore.benchmark.workload;

import lombok.experimental.UtilityClass;
import org.naviqore.benchmark.subject.Subject;
import org.naviqore.benchmark.workload.Query.Location;
import org.naviqore.benchmark.workload.Query.TimeType;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.time.zone.ZoneOffsetTransition;
import java.util.ArrayList;
import java.util.List;

/**
 * The workloads of the benchmarks, drawn from the timetable of a version: from the dates of its validity, from hubs
 * found by name and from stops drawn at random with fixed seeds, so that every version and every run gets the same
 * queries.
 * <ul>
 *     <li>{@value #ANSWERS}: the queries {@code compare} compares the answers of, 1 504 on the Swiss national feed.
 *     Connections at three times of a winter weekday, a Saturday, a Sunday, New Year's Day, the days of a change of
 *     daylight saving time (and at night on those), the first and the last day of the timetable and a day after it,
 *     between hubs, between random stops and from random stops to hubs; connections with each query configuration;
 *     connections from and to coordinates; and isolines from hubs and random stops, departing and arriving.</li>
 *     <li>{@value #CONNECTIONS}: 450 connections between random stops on a winter weekday, departing at 07:30, 12:30
 *     and 17:30.</li>
 *     <li>{@value #ISOLINES}: 20 isolines from random stops on a winter weekday, departing at 08:00.</li>
 *     <li>{@value #WINDOW_ISOLINES}: isolines with a time window of 60 minutes on a winter weekday, from four hubs
 *     departing at 08:00, and from one arriving at 18:00.</li>
 *     <li>{@value #WINDOW_CONNECTIONS}: connections with a time window of 60 minutes on a winter weekday, between four
 *     hubs and between 20 pairs of random stops, departing at 07:30 and arriving at 17:30.</li>
 * </ul>
 * The hubs are stations of the Swiss national feed; on another feed, a name no stop has draws a random stop.
 */
@UtilityClass
public class Workloads {

    public static final String ANSWERS = "answers";
    public static final String CONNECTIONS = "connections";
    public static final String ISOLINES = "isolines";
    public static final String WINDOW_ISOLINES = "window-isolines";
    public static final String WINDOW_CONNECTIONS = "window-connections";

    private static final List<String> HUB_NAMES = List.of("Zürich HB", "Bern", "Genève", "Basel SBB", "Lausanne",
            "Luzern", "Lugano", "Chur", "St. Gallen", "Interlaken Ost", "Zermatt", "Brig", "Winterthur", "Olten",
            "Bellinzona", "Neuchâtel");
    private static final List<String> WINDOW_DEPARTURE_HUB_NAMES = List.of("Zürich HB", "Bern", "Lausanne", "Chur");
    private static final String WINDOW_ARRIVAL_HUB_NAME = "Bern";

    // query configurations, see Query
    private static final String DEFAULT = "default";
    private static final String WINDOW = "window60";
    private static final List<String> CONFIGURATIONS = List.of("rail", "bus", "bus+tram", "transfers1", "walk5",
            "mintransfer5", "travel60", WINDOW);
    private static final List<String> ISOLINE_CONFIGURATIONS = List.of(DEFAULT, "rail", "bus+tram");

    // random stops: the seeds, and how many each workload draws
    private static final long ANSWERS_SEED = 42;
    private static final long TIMING_SEED = 7;
    private static final int ANSWERS_STOPS = 16;
    private static final int ANSWERS_ISOLINE_SOURCES = 40;
    private static final int TIMING_CONNECTIONS_PER_TIME = 150;
    private static final int TIMING_ISOLINES = 20;
    private static final int TIMING_WINDOW_CONNECTIONS = 20;

    /**
     * The workload of a name, drawn from the timetable of a version.
     *
     * @param name    one of {@link #ANSWERS}, {@link #CONNECTIONS}, {@link #ISOLINES}, {@link #WINDOW_ISOLINES} and
     *                {@link #WINDOW_CONNECTIONS}
     * @param subject the subject of the version, whose service is built
     */
    public static Workload of(String name, Subject subject) {
        Days days = Days.of(subject);
        return switch (name) {
            case ANSWERS -> answers(subject, days);
            case CONNECTIONS -> connections(subject, days);
            case ISOLINES -> isolines(subject, days);
            case WINDOW_ISOLINES -> windowIsolines(subject, days);
            case WINDOW_CONNECTIONS -> windowConnections(subject, days);
            default -> throw new IllegalArgumentException(
                    String.format("Unknown workload %s, the workloads are %s, %s, %s, %s and %s", name, ANSWERS,
                            CONNECTIONS, ISOLINES, WINDOW_ISOLINES, WINDOW_CONNECTIONS));
        };
    }

    private static Workload answers(Subject subject, Days days) {
        List<String> hubs = HUB_NAMES.stream().map(subject::stopByName).toList();
        List<String> stops = subject.randomStops(ANSWERS_STOPS, ANSWERS_SEED);
        int half = ANSWERS_STOPS / 2;

        // pairs of stops: hub to hub, random stop to random stop, and random stop to hub
        List<Pair> pairs = new ArrayList<>();
        for (int i = 0; i < half; i++) {
            pairs.add(new Pair(hubs.get(i), hubs.get((i + 5) % hubs.size())));
            pairs.add(new Pair(stops.get(i), stops.get((i + 1) % half)));
            pairs.add(new Pair(stops.get(half + i), hubs.get(half + i)));
        }

        List<LocalDate> dates = new ArrayList<>(List.of(days.winter(), days.saturday(), days.sunday(),
                LocalDate.of(days.start().getYear() + 1, Month.JANUARY, 1)));
        dates.addAll(days.transitions());
        dates.addAll(List.of(days.start(), days.end(), days.end().plusDays(10)));

        List<Query> queries = new ArrayList<>();
        for (LocalDate date : dates) {
            for (Pair pair : pairs) {
                queries.add(pair.connections(days.at(date, 7, 30), TimeType.DEPARTURE, DEFAULT));
                queries.add(pair.connections(days.at(date, 17, 45), TimeType.ARRIVAL, DEFAULT));
                queries.add(pair.connections(days.at(date, 23, 40), TimeType.DEPARTURE, DEFAULT));
                // the night of a change of daylight saving time
                if (days.transitions().contains(date)) {
                    queries.add(pair.connections(days.at(date, 2, 30), TimeType.DEPARTURE, DEFAULT));
                    queries.add(pair.connections(days.at(date, 3, 30), TimeType.ARRIVAL, DEFAULT));
                }
            }
        }

        List<LocalDate> configurationDates = new ArrayList<>(List.of(days.winter()));
        configurationDates.addAll(days.transitions());
        for (String configuration : CONFIGURATIONS) {
            for (LocalDate date : configurationDates) {
                for (Pair pair : pairs) {
                    queries.add(pair.connections(days.at(date, 7, 30), TimeType.DEPARTURE, configuration));
                }
            }
        }

        // from and to coordinates near the stops instead of the stops
        OffsetDateTime winterMorning = days.at(days.winter(), 7, 30);
        for (Pair pair : pairs) {
            queries.add(Query.connections(Location.near(pair.from()), Location.near(pair.to()), winterMorning,
                    TimeType.DEPARTURE, DEFAULT));
            queries.add(Query.connections(Location.stop(pair.from()), Location.near(pair.to()), winterMorning,
                    TimeType.DEPARTURE, DEFAULT));
            queries.add(Query.connections(Location.near(pair.from()), Location.stop(pair.to()), winterMorning,
                    TimeType.DEPARTURE, DEFAULT));
        }

        List<String> sources = List.of(hubs.get(0), hubs.get(1), hubs.get(2), stops.get(0), stops.get(1), stops.get(2));
        LocalDate secondDay = days.transitions().isEmpty() ? days.saturday() : days.transitions().getFirst();
        for (String configuration : ISOLINE_CONFIGURATIONS) {
            for (LocalDate date : List.of(days.winter(), secondDay)) {
                for (String source : sources) {
                    queries.add(Query.isolines(Location.stop(source), days.at(date, 8, 0), TimeType.DEPARTURE,
                            configuration));
                    queries.add(Query.isolines(Location.stop(source), days.at(date, 18, 0), TimeType.ARRIVAL,
                            configuration));
                }
            }
        }
        for (String source : subject.randomStops(ANSWERS_ISOLINE_SOURCES, TIMING_SEED)) {
            queries.add(
                    Query.isolines(Location.stop(source), days.at(days.winter(), 8, 0), TimeType.DEPARTURE, DEFAULT));
        }

        return new Workload(ANSWERS, queries);
    }

    private static Workload connections(Subject subject, Days days) {
        List<String> stops = subject.randomStops(2 * TIMING_CONNECTIONS_PER_TIME, TIMING_SEED);
        List<Query> queries = new ArrayList<>();
        for (int hour : new int[]{7, 12, 17}) {
            OffsetDateTime time = days.at(days.winter(), hour, 30);
            for (int i = 0; i < TIMING_CONNECTIONS_PER_TIME; i++) {
                queries.add(new Pair(stops.get(2 * i), stops.get(2 * i + 1)).connections(time, TimeType.DEPARTURE,
                        DEFAULT));
            }
        }

        return new Workload(CONNECTIONS, queries);
    }

    private static Workload isolines(Subject subject, Days days) {
        // the stops after those of the connections, drawn with the same seed
        List<String> stops = subject.randomStops(2 * TIMING_CONNECTIONS_PER_TIME + TIMING_ISOLINES, TIMING_SEED);
        OffsetDateTime time = days.at(days.winter(), 8, 0);
        List<Query> queries = new ArrayList<>();
        for (String source : stops.subList(2 * TIMING_CONNECTIONS_PER_TIME, stops.size())) {
            queries.add(Query.isolines(Location.stop(source), time, TimeType.DEPARTURE, DEFAULT));
        }

        return new Workload(ISOLINES, queries);
    }

    private static Workload windowIsolines(Subject subject, Days days) {
        List<Query> queries = new ArrayList<>();
        for (String hub : WINDOW_DEPARTURE_HUB_NAMES) {
            queries.add(Query.isolines(Location.stop(subject.stopByName(hub)), days.at(days.winter(), 8, 0),
                    TimeType.DEPARTURE, WINDOW));
        }
        queries.add(Query.isolines(Location.stop(subject.stopByName(WINDOW_ARRIVAL_HUB_NAME)),
                days.at(days.winter(), 18, 0), TimeType.ARRIVAL, WINDOW));

        return new Workload(WINDOW_ISOLINES, queries);
    }

    private static Workload windowConnections(Subject subject, Days days) {
        // the first pairs of random stops of the connections, drawn with the same seed
        List<String> stops = subject.randomStops(2 * TIMING_WINDOW_CONNECTIONS, TIMING_SEED);
        List<String> hubs = WINDOW_DEPARTURE_HUB_NAMES.stream().map(subject::stopByName).toList();
        List<Pair> pairs = new ArrayList<>();
        for (int i = 0; i < hubs.size(); i++) {
            pairs.add(new Pair(hubs.get(i), hubs.get((i + 1) % hubs.size())));
        }
        for (int i = 0; i < TIMING_WINDOW_CONNECTIONS; i++) {
            pairs.add(new Pair(stops.get(2 * i), stops.get(2 * i + 1)));
        }

        List<Query> queries = new ArrayList<>();
        for (Pair pair : pairs) {
            queries.add(pair.connections(days.at(days.winter(), 7, 30), TimeType.DEPARTURE, WINDOW));
            queries.add(pair.connections(days.at(days.winter(), 17, 30), TimeType.ARRIVAL, WINDOW));
        }

        return new Workload(WINDOW_CONNECTIONS, queries);
    }

    private record Pair(String from, String to) {

        Query connections(OffsetDateTime time, TimeType timeType, String configuration) {
            return Query.connections(Location.stop(from), Location.stop(to), time, timeType, configuration);
        }
    }

    /**
     * The days the workloads query, in the time zone of the timetable.
     *
     * @param zone        the time zone of the timetable
     * @param start       the first day of the timetable
     * @param end         the last day of the timetable
     * @param winter      a Wednesday a month after the start
     * @param saturday    a Saturday about five months after the start
     * @param sunday      a Sunday about seven months after the start
     * @param transitions the days of a change of daylight saving time
     */
    private record Days(ZoneId zone, LocalDate start, LocalDate end, LocalDate winter, LocalDate saturday,
                        LocalDate sunday, List<LocalDate> transitions) {

        static Days of(Subject subject) {
            ZoneId zone = ZoneId.of(subject.zone());
            List<String> validity = subject.validity();
            LocalDate start = LocalDate.parse(validity.get(0));
            LocalDate end = LocalDate.parse(validity.get(1));
            return new Days(zone, start, end,
                    start.plusDays(31).with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY)),
                    start.plusDays(150).with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)),
                    start.plusDays(200).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)),
                    transitions(zone, start, end));
        }

        private static List<LocalDate> transitions(ZoneId zone, LocalDate start, LocalDate end) {
            List<LocalDate> days = new ArrayList<>();
            Instant from = start.atStartOfDay(zone).toInstant();
            Instant until = end.plusDays(1).atStartOfDay(zone).toInstant();
            ZoneOffsetTransition next;
            while ((next = zone.getRules().nextTransition(from)) != null && next.getInstant().isBefore(until)) {
                days.add(next.getDateTimeBefore().toLocalDate());
                from = next.getInstant();
            }

            return days;
        }

        OffsetDateTime at(LocalDate day, int hour, int minute) {
            return day.atTime(hour, minute).atZone(zone).toOffsetDateTime();
        }
    }

}
