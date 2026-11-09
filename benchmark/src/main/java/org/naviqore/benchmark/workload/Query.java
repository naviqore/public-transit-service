package org.naviqore.benchmark.workload;

import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

/**
 * A query of a workload: the connections between two locations, or the isolines from one, at a time.
 * <p>
 * A configuration names the options of the query as tokens joined by {@code +}, e.g. {@code rail+transfers1}:
 * <ul>
 *     <li>{@code default}: no option set;</li>
 *     <li>travel modes in lower case: {@code bus}, {@code tram}, {@code rail}, {@code ship}, {@code subway},
 *     {@code aerial} and {@code funicular};</li>
 *     <li>{@code wheelchair} and {@code bike}: accessible trips only;</li>
 *     <li>{@code transfers0} and {@code transfers1}: at most no or one transfer;</li>
 *     <li>{@code walk5}: walks of at most 5 minutes; {@code mintransfer5}: transfers of at least 5 minutes;</li>
 *     <li>{@code travel60}: a travel time of at most 60 minutes; {@code window60}: a time window of 60 minutes.</li>
 * </ul>
 *
 * @param kind          connections or isolines
 * @param source        where the query starts
 * @param target        where connections lead to, {@code null} for isolines
 * @param time          the time of departure or arrival
 * @param timeType      whether the time is a departure or an arrival
 * @param configuration the options of the query
 */
public record Query(Kind kind, Location source, @Nullable Location target, OffsetDateTime time, TimeType timeType,
                    String configuration) {

    private static final String NO_TARGET = "-";

    public Query {
        if ((kind == Kind.ISOLINES) != (target == null)) {
            throw new IllegalArgumentException("Connections need a target, isolines take none");
        }
    }

    public static Query connections(Location source, Location target, OffsetDateTime time, TimeType timeType,
                                    String configuration) {
        return new Query(Kind.CONNECTIONS, source, target, time, timeType, configuration);
    }

    public static Query isolines(Location source, OffsetDateTime time, TimeType timeType, String configuration) {
        return new Query(Kind.ISOLINES, source, null, time, timeType, configuration);
    }

    /**
     * Reads a query from its fields, as {@link #encode()} writes them.
     */
    public static Query decode(List<String> fields) {
        if (fields.size() != 6) {
            throw new IllegalArgumentException("A query has 6 fields, not " + fields.size() + ": " + fields);
        }
        String target = fields.get(2);
        return new Query(Kind.valueOf(fields.get(0)), Location.decode(fields.get(1)),
                target.equals(NO_TARGET) ? null : Location.decode(target), OffsetDateTime.parse(fields.get(3)),
                TimeType.valueOf(fields.get(4)), fields.get(5));
    }

    /**
     * Writes the query as a list of fields, which is how a {@link org.naviqore.benchmark.subject.Subject} takes it.
     */
    public List<String> encode() {
        return List.of(kind.name(), source.encode(), target == null ? NO_TARGET : target.encode(), time.toString(),
                timeType.name(), configuration);
    }

    @Override
    public String toString() {
        String locations = target == null ? source.encode() : source.encode() + " > " + target.encode();
        return String.join(" ", kind.name().toLowerCase(Locale.ROOT), time.toString(),
                timeType.name().toLowerCase(Locale.ROOT), locations, configuration);
    }

    public enum Kind {
        CONNECTIONS,
        ISOLINES
    }

    public enum TimeType {
        DEPARTURE,
        ARRIVAL
    }

    /**
     * A location a query starts from or leads to: a stop, or a coordinate near a stop, a short walk away from it.
     *
     * @param type   a stop or a coordinate near a stop
     * @param stopId the id of the stop
     */
    public record Location(Type type, String stopId) {

        public static Location stop(String stopId) {
            return new Location(Type.STOP, stopId);
        }

        public static Location near(String stopId) {
            return new Location(Type.NEAR, stopId);
        }

        static Location decode(String text) {
            int colon = text.indexOf(':');
            if (colon < 0) {
                throw new IllegalArgumentException("A location is type:stopId, not " + text);
            }
            return new Location(Type.valueOf(text.substring(0, colon).toUpperCase(Locale.ROOT)),
                    text.substring(colon + 1));
        }

        String encode() {
            return type.name().toLowerCase(Locale.ROOT) + ":" + stopId;
        }

        public enum Type {
            STOP,
            NEAR
        }
    }

}
