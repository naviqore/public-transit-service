package org.naviqore.benchmark.workload;

import java.util.List;

/**
 * A named set of queries a benchmark runs.
 *
 * @param name    the name of the workload, as {@link Workloads} knows it
 * @param queries the queries, in the order they run
 */
public record Workload(String name, List<Query> queries) {
}
