package org.naviqore.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.naviqore.gtfs.schedule.model.Stop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StopSortStrategyTest {

    @Mock
    private Stop stopGstaad;
    @Mock
    private Stop stopGstaadBahnhof;
    @Mock
    private Stop stopGrundGstaad;
    @Mock
    private Stop stopAnother;
    @Mock
    private Stop stopGstaadDuplicate;

    private List<Stop> testStops;

    @BeforeEach
    void setUp() {
        when(stopGstaad.getName()).thenReturn("Gstaad");
        when(stopGstaadBahnhof.getName()).thenReturn("Gstaad, Bahnhof");
        when(stopGrundGstaad.getName()).thenReturn("Grund b. Gstaad");
        when(stopAnother.getName()).thenReturn("Another Place");
        when(stopGstaadDuplicate.getName()).thenReturn("Gstaad");

        testStops = new ArrayList<>(
                List.of(stopGstaad, stopGstaadBahnhof, stopGrundGstaad, stopAnother, stopGstaadDuplicate));

        Collections.shuffle(testStops);
    }

    @Test
    void getComparator_withRelevanceSort_shouldOrderByScoreLengthThenName() {
        String query = "Gstaa";
        Comparator<Stop> comparator = StopSortStrategy.RELEVANCE.getComparator(query);

        List<String> expectedOrder = List.of("Gstaad", "Gstaad", "Gstaad, Bahnhof", "Grund b. Gstaad");

        List<String> actualOrder = testStops.stream()
                .filter(s -> s.getName().toLowerCase().contains(query.toLowerCase()))
                .sorted(comparator)
                .map(Stop::getName)
                .collect(Collectors.toList());

        assertIterableEquals(expectedOrder, actualOrder);
    }

    @Test
    void getComparator_withExactMatchQuery_shouldPlaceExactMatchFirst() {
        String query = "Gstaad";
        Comparator<Stop> comparator = StopSortStrategy.RELEVANCE.getComparator(query);

        List<String> expectedOrder = List.of("Gstaad", "Gstaad", "Gstaad, Bahnhof", "Grund b. Gstaad");

        List<String> actualOrder = testStops.stream()
                .filter(s -> s.getName().toLowerCase().contains(query.toLowerCase()))
                .sorted(comparator)
                .map(Stop::getName)
                .collect(Collectors.toList());

        assertIterableEquals(expectedOrder, actualOrder);
    }

    @Test
    void getComparator_withMixedCaseQuery_shouldBeCaseInsensitive() {
        String query = "gStAaD";
        Comparator<Stop> comparator = StopSortStrategy.RELEVANCE.getComparator(query);

        List<String> expectedOrder = List.of("Gstaad", "Gstaad", "Gstaad, Bahnhof", "Grund b. Gstaad");

        List<String> actualOrder = testStops.stream()
                .filter(s -> s.getName().toLowerCase().contains(query.toLowerCase()))
                .sorted(comparator)
                .map(Stop::getName)
                .collect(Collectors.toList());

        assertIterableEquals(expectedOrder, actualOrder);
    }

    @Test
    void getComparator_withAlphabeticalSort_shouldOrderByNameOnly() {
        String query = "any";
        Comparator<Stop> comparator = StopSortStrategy.ALPHABETICAL.getComparator(query);

        List<String> expectedOrder = List.of("Another Place", "Grund b. Gstaad", "Gstaad", "Gstaad", "Gstaad, Bahnhof");

        List<String> actualOrder = testStops.stream()
                .sorted(comparator)
                .map(Stop::getName)
                .collect(Collectors.toList());

        assertIterableEquals(expectedOrder, actualOrder);
    }
}