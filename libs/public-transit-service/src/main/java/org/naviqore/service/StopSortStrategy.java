package org.naviqore.service;

import org.naviqore.gtfs.schedule.model.Stop;

import java.util.Comparator;
import java.util.function.Function;

/**
 * Sorting strategies for stop search results.
 */
public enum StopSortStrategy {

    /**
     * Sorts stops alphabetically by name.
     */
    ALPHABETICAL {
        @Override
        public <T> Comparator<T> getComparator(String query, Function<T, String> nameExtractor) {
            return Comparator.comparing(nameExtractor);
        }
    },

    /**
     * Sorts stops by relevance to the search query. The relevance is determined by:
     * <ul>
     *   <li>Exact match (score 0)</li>
     *   <li>Starts with the query (score 1)</li>
     *   <li>Contains the query (score 2)</li>
     * </ul>
     * Tie-breaking is done by name length (shorter is better), then alphabetically.
     */
    RELEVANCE {
        @Override
        public <T> Comparator<T> getComparator(String query, Function<T, String> nameExtractor) {
            String lowerCaseQuery = query.toLowerCase();

            return (s1, s2) -> {
                String name1 = nameExtractor.apply(s1).toLowerCase();
                String name2 = nameExtractor.apply(s2).toLowerCase();

                int score1 = calculateScore(name1, lowerCaseQuery);
                int score2 = calculateScore(name2, lowerCaseQuery);

                // primary sort: by relevance score (lower is better)
                if (score1 != score2) {
                    return Integer.compare(score1, score2);
                }

                // secondary sort: by name length (shorter is better)
                if (name1.length() != name2.length()) {
                    return Integer.compare(name1.length(), name2.length());
                }

                // tertiary sort: alphabetically
                return nameExtractor.apply(s1).compareTo(nameExtractor.apply(s2));
            };
        }

        private int calculateScore(String name, String query) {
            if (name.equals(query)) {
                return 0;
            }
            if (name.startsWith(query)) {
                return 1;
            }

            return 2;
        }
    };

    /**
     * Get the comparator for the specific strategy.
     */
    public Comparator<Stop> getComparator(String query) {
        return getComparator(query, Stop::getName);
    }

    /**
     * Get the comparator for the specific strategy, for any type of stop that can provide its name.
     */
    public abstract <T> Comparator<T> getComparator(String query, Function<T, String> nameExtractor);
}