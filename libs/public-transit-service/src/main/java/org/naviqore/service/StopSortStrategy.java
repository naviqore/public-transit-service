package org.naviqore.service;

import org.naviqore.gtfs.schedule.model.Stop;

import java.util.Comparator;

/**
 * Sorting strategies for stop search results.
 */
public enum StopSortStrategy {

    /**
     * Sorts stops alphabetically by name.
     */
    ALPHABETICAL {
        @Override
        public Comparator<Stop> getComparator(String query) {
            return Comparator.comparing(Stop::getName);
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
        public Comparator<Stop> getComparator(String query) {
            String lowerCaseQuery = query.toLowerCase();

            return (s1, s2) -> {
                String name1 = s1.getName().toLowerCase();
                String name2 = s2.getName().toLowerCase();

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
                return s1.getName().compareTo(s2.getName());
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
    public abstract Comparator<Stop> getComparator(String query);
}