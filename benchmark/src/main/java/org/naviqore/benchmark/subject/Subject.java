package org.naviqore.benchmark.subject;

import java.util.List;

/**
 * A version of the libraries as a benchmark sees it: it reads a GTFS feed, builds the public transit service on it,
 * answers queries and times them.
 * <p>
 * Two versions are compared in one JVM, each in a class loader of its own, and a benchmark calls them through this
 * interface by method name. Parameters and results are therefore JDK types only, so that two versions can differ in
 * everything else. A query is the list of its fields, as {@link org.naviqore.benchmark.workload.Query#encode()} writes
 * it.
 */
public interface Subject {

    /**
     * Reads a GTFS feed into a timetable.
     *
     * @param feed the path of the ZIP file of the feed
     */
    void read(String feed);

    /**
     * Builds the public transit service on the timetable read.
     */
    void build();

    /**
     * Returns what the libraries logged at info level or above while reading the feed and building the service, one
     * line per event: its time in milliseconds since the epoch, the logger and the message, separated by tabs.
     */
    List<String> log();

    /**
     * Returns the first and the last date of the timetable, in ISO format.
     */
    List<String> validity();

    /**
     * Returns the time zone most routes of the timetable run in.
     */
    String zone();

    /**
     * Returns the id of a stop with the given name, or of a stop drawn at random if no stop is called so.
     */
    String stopByName(String name);

    /**
     * Returns the ids of stops drawn at random.
     *
     * @param count the number of stops
     * @param seed  the seed of the random draws
     */
    List<String> randomStops(int count, long seed);

    /**
     * Answers a query: a line per connection in the order of the service, or a line per stop an isoline reaches, sorted
     * by stop; if the query fails, a single line naming the failure.
     */
    List<String> answer(List<String> query);

    /**
     * Runs a query and measures it: its wall time and the CPU time of the calling thread in nanoseconds, the bytes it
     * allocated on the calling thread, and the number of its answers, or -1 if it failed.
     */
    long[] time(List<String> query);

}
