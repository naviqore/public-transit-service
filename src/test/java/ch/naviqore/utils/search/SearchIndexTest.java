package ch.naviqore.utils.search;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.ToString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SearchIndexTest {

    private SearchIndex<SearchCase> index;

    @BeforeEach
    void setUp() {
        index = new SearchIndex<>();
        for (SearchCase searchCase : SearchCase.values()) {
            index.add(searchCase.value, searchCase);
        }
    }

    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @ToString
    enum SearchCase {
        EMPTY(""),
        LEFT_PADDED("    left-padded"),
        RIGHT_PADDED("right-padded    "),
        SPECIAL_CHARACTER("$äö/üö)("),
        NUMBERS("8187123"),
        SERIES_1("AAA"),
        SERIES_2("AAABB"),
        SERIES_3("AAABBC"),
        SERIES_4("AAABBCDD"),
        SERIES_5("AAABBCDDEEE");

        final String value;
    }

    @Nested
    class InputValidation {

        @Test
        void shouldThrowWhenKeyAlreadyExists() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
                index.add(SearchCase.SERIES_1.value, SearchCase.SERIES_1);
            });
            assertEquals("Exact search key already exists: AAA", thrown.getMessage());
        }
    }

    @Nested
    class StartsWith {

        @Test
        void shouldNotFindEmptyKey() {
            List<SearchCase> result = index.search("", SearchIndex.SearchStrategy.STARTS_WITH);
            assertEquals(List.of(), result);
        }

        @Test
        void shouldFindLeftPadded() {
            List<SearchCase> result = index.search("    left-padded", SearchIndex.SearchStrategy.STARTS_WITH);
            assertEquals(List.of(SearchCase.LEFT_PADDED), result);
        }

        @Test
        void shouldFindRightPadded() {
            List<SearchCase> result = index.search("right-padded", SearchIndex.SearchStrategy.STARTS_WITH);
            assertEquals(List.of(SearchCase.RIGHT_PADDED), result);
        }

        @Test
        void shouldFindSpecialCharacter() {
            List<SearchCase> result = index.search("$äö/üö)(", SearchIndex.SearchStrategy.STARTS_WITH);
            assertEquals(List.of(SearchCase.SPECIAL_CHARACTER), result);
        }

        @Test
        void shouldFindNumbers() {
            List<SearchCase> result = index.search("8187123", SearchIndex.SearchStrategy.STARTS_WITH);
            assertEquals(List.of(SearchCase.NUMBERS), result);
        }

        @Test
        void shouldFindSeries() {
            List<SearchCase> result = index.search("A", SearchIndex.SearchStrategy.STARTS_WITH);
            assertEquals(List.of(SearchCase.SERIES_1, SearchCase.SERIES_2, SearchCase.SERIES_3, SearchCase.SERIES_4,
                    SearchCase.SERIES_5), result);
        }

        @Test
        void shouldNotFindSeries() {
            List<SearchCase> result = index.search("B", SearchIndex.SearchStrategy.STARTS_WITH);
            assertTrue(result.isEmpty());
        }

        @Test
        void shouldNotFindMissingKey() {
            List<SearchCase> result = index.search("missing", SearchIndex.SearchStrategy.STARTS_WITH);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class EndsWith {

        @Test
        void shouldNotFindEmptyKey() {
            List<SearchCase> result = index.search("", SearchIndex.SearchStrategy.ENDS_WITH);
            assertEquals(List.of(), result);
        }

        @Test
        void shouldFindLeftPadded() {
            List<SearchCase> result = index.search("left-padded", SearchIndex.SearchStrategy.ENDS_WITH);
            assertEquals(List.of(SearchCase.LEFT_PADDED), result);
        }

        @Test
        void shouldFindRightPadded() {
            List<SearchCase> result = index.search("    ", SearchIndex.SearchStrategy.ENDS_WITH);
            assertEquals(List.of(SearchCase.RIGHT_PADDED), result);
        }

        @Test
        void shouldFindSpecialCharacter() {
            List<SearchCase> result = index.search(")(", SearchIndex.SearchStrategy.ENDS_WITH);
            assertEquals(List.of(SearchCase.SPECIAL_CHARACTER), result);
        }

        @Test
        void shouldFindNumbers() {
            List<SearchCase> result = index.search("7123", SearchIndex.SearchStrategy.ENDS_WITH);
            assertEquals(List.of(SearchCase.NUMBERS), result);
        }

        @Test
        void shouldFindSeries() {
            List<SearchCase> result = index.search("E", SearchIndex.SearchStrategy.ENDS_WITH);
            assertEquals(List.of(SearchCase.SERIES_5), result);
        }

        @Test
        void shouldFindSeriesInTheMiddle() {
            List<SearchCase> result = index.search("C", SearchIndex.SearchStrategy.ENDS_WITH);
            assertEquals(List.of(SearchCase.SERIES_3), result);
        }

        @Test
        void shouldNotFindMissingKey() {
            List<SearchCase> result = index.search("missing", SearchIndex.SearchStrategy.ENDS_WITH);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class Contains {

        @Test
        void shouldNotFindEmptyKey() {
            List<SearchCase> result = index.search("", SearchIndex.SearchStrategy.CONTAINS);
            assertEquals(List.of(), result);
        }

        @Test
        void shouldFindLeftPadded() {
            List<SearchCase> result = index.search("left", SearchIndex.SearchStrategy.CONTAINS);
            assertEquals(List.of(SearchCase.LEFT_PADDED), result);
        }

        @Test
        void shouldFindRightPadded() {
            List<SearchCase> result = index.search("right", SearchIndex.SearchStrategy.CONTAINS);
            assertEquals(List.of(SearchCase.RIGHT_PADDED), result);
        }

        @Test
        void shouldFindSpecialCharacter() {
            List<SearchCase> result = index.search("äö", SearchIndex.SearchStrategy.CONTAINS);
            assertEquals(List.of(SearchCase.SPECIAL_CHARACTER), result);
        }

        @Test
        void shouldFindNumbers() {
            List<SearchCase> result = index.search("187", SearchIndex.SearchStrategy.CONTAINS);
            assertEquals(List.of(SearchCase.NUMBERS), result);
        }

        @Test
        void shouldFindSeries() {
            List<SearchCase> result = index.search("B", SearchIndex.SearchStrategy.CONTAINS);
            List<SearchCase> expected = List.of(SearchCase.SERIES_2, SearchCase.SERIES_3, SearchCase.SERIES_4,
                    SearchCase.SERIES_5);
            assertEquals(expected.size(), result.size());
            assertTrue(result.containsAll(expected));
        }

        @Test
        void shouldNotFindMissingKey() {
            List<SearchCase> result = index.search("missing", SearchIndex.SearchStrategy.CONTAINS);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class Exact {

        @Test
        void shouldNotFindEmptyKey() {
            List<SearchCase> result = index.search("", SearchIndex.SearchStrategy.EXACT);
            assertEquals(List.of(), result);
        }

        @Test
        void shouldFindLeftPadded() {
            List<SearchCase> result = index.search("    left-padded", SearchIndex.SearchStrategy.EXACT);
            assertEquals(List.of(SearchCase.LEFT_PADDED), result);
        }

        @Test
        void shouldFindRightPadded() {
            List<SearchCase> result = index.search("right-padded    ", SearchIndex.SearchStrategy.EXACT);
            assertEquals(List.of(SearchCase.RIGHT_PADDED), result);
        }

        @Test
        void shouldFindSpecialCharacter() {
            List<SearchCase> result = index.search("$äö/üö)(", SearchIndex.SearchStrategy.EXACT);
            assertEquals(List.of(SearchCase.SPECIAL_CHARACTER), result);
        }

        @Test
        void shouldFindNumbers() {
            List<SearchCase> result = index.search("8187123", SearchIndex.SearchStrategy.EXACT);
            assertEquals(List.of(SearchCase.NUMBERS), result);
        }

        @Test
        void shouldFindSeries() {
            List<SearchCase> result = index.search("AAA", SearchIndex.SearchStrategy.EXACT);
            assertEquals(List.of(SearchCase.SERIES_1), result);
        }

        @Test
        void shouldNotFindMissingKey() {
            List<SearchCase> result = index.search("missing", SearchIndex.SearchStrategy.EXACT);
            assertTrue(result.isEmpty());
        }
    }
}
