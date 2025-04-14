package org.naviqore.utils.search;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.ToString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchIndexTest {

    private SearchIndexBuilder<SearchCase> builder;

    @BeforeEach
    void setUp() {
        builder = SearchIndex.builder();
        for (SearchCase searchCase : SearchCase.values()) {
            builder.add(searchCase.value, searchCase);
        }
    }

    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @ToString
    enum SearchCase {
        LEFT_PADDED("    left-padded"),
        RIGHT_PADDED("right-padded    "),
        SPECIAL_CHARACTER("$äö/üö)("),
        NUMBERS("8187123"),
        DUPLICATE_1("duplicate"),
        DUPLICATE_2("duplicate"),
        SERIES_1("AAA"),
        SERIES_2("AAABB"),
        SERIES_3("AAABBC"),
        SERIES_4("AAABBCDD"),
        SERIES_5("AAABBCDDEEE");

        final String value;
    }

    @Nested
    class Builder {

        @Test
        void shouldThrowWhenKeyIsNull() {
            assertThatThrownBy(() -> builder.add(null, SearchCase.SERIES_1)).isInstanceOf(
                    IllegalArgumentException.class).hasMessage("Key cannot be null or empty.");
        }

        @Test
        void shouldThrowWhenKeyIsEmpty() {
            assertThatThrownBy(() -> builder.add("", SearchCase.SERIES_1)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Key cannot be null or empty.");
        }
    }

    @Nested
    class Search {

        private SearchIndex<SearchCase> index;

        @BeforeEach
        void setUp() {
            index = builder.build();
        }

        @Nested
        class StartsWith {

            @Test
            void shouldNotFindEmptyKey() {
                Set<SearchCase> result = index.search("", SearchIndex.SearchStrategy.STARTS_WITH);
                assertThat(result).isEmpty();
            }

            @Test
            void shouldFindLeftPadded() {
                Set<SearchCase> result = index.search("    left-padded", SearchIndex.SearchStrategy.STARTS_WITH);
                assertThat(result).containsExactly(SearchCase.LEFT_PADDED);
            }

            @Test
            void shouldFindRightPadded() {
                Set<SearchCase> result = index.search("right-padded", SearchIndex.SearchStrategy.STARTS_WITH);
                assertThat(result).containsExactly(SearchCase.RIGHT_PADDED);
            }

            @Test
            void shouldFindSpecialCharacter() {
                Set<SearchCase> result = index.search("$äö/üö)(", SearchIndex.SearchStrategy.STARTS_WITH);
                assertThat(result).containsExactly(SearchCase.SPECIAL_CHARACTER);
            }

            @Test
            void shouldFindNumbers() {
                Set<SearchCase> result = index.search("8187123", SearchIndex.SearchStrategy.STARTS_WITH);
                assertThat(result).containsExactly(SearchCase.NUMBERS);
            }

            @Test
            void shouldFindSeries() {
                Set<SearchCase> result = index.search("A", SearchIndex.SearchStrategy.STARTS_WITH);
                assertThat(result).containsExactlyInAnyOrder(SearchCase.SERIES_1, SearchCase.SERIES_2,
                        SearchCase.SERIES_3, SearchCase.SERIES_4, SearchCase.SERIES_5);
            }

            @Test
            void shouldNotFindSeries() {
                Set<SearchCase> result = index.search("B", SearchIndex.SearchStrategy.STARTS_WITH);
                assertThat(result).isEmpty();
            }

            @Test
            void shouldNotFindMissingKey() {
                Set<SearchCase> result = index.search("missing", SearchIndex.SearchStrategy.STARTS_WITH);
                assertThat(result).isEmpty();
            }

            @Test
            void shouldFindDuplicates() {
                Set<SearchCase> result = index.search("duplicate", SearchIndex.SearchStrategy.STARTS_WITH);
                assertThat(result).containsExactlyInAnyOrderElementsOf(
                        List.of(SearchCase.DUPLICATE_1, SearchCase.DUPLICATE_2));
            }
        }

        @Nested
        class EndsWith {

            @Test
            void shouldNotFindEmptyKey() {
                Set<SearchCase> result = index.search("", SearchIndex.SearchStrategy.ENDS_WITH);
                assertThat(result).isEmpty();
            }

            @Test
            void shouldFindLeftPadded() {
                Set<SearchCase> result = index.search("left-padded", SearchIndex.SearchStrategy.ENDS_WITH);
                assertThat(result).containsExactly(SearchCase.LEFT_PADDED);
            }

            @Test
            void shouldFindRightPadded() {
                Set<SearchCase> result = index.search("    ", SearchIndex.SearchStrategy.ENDS_WITH);
                assertThat(result).containsExactly(SearchCase.RIGHT_PADDED);
            }

            @Test
            void shouldFindSpecialCharacter() {
                Set<SearchCase> result = index.search(")(", SearchIndex.SearchStrategy.ENDS_WITH);
                assertThat(result).containsExactly(SearchCase.SPECIAL_CHARACTER);
            }

            @Test
            void shouldFindNumbers() {
                Set<SearchCase> result = index.search("7123", SearchIndex.SearchStrategy.ENDS_WITH);
                assertThat(result).containsExactly(SearchCase.NUMBERS);
            }

            @Test
            void shouldFindSeries() {
                Set<SearchCase> result = index.search("E", SearchIndex.SearchStrategy.ENDS_WITH);
                assertThat(result).containsExactly(SearchCase.SERIES_5);
            }

            @Test
            void shouldFindSeriesInTheMiddle() {
                Set<SearchCase> result = index.search("C", SearchIndex.SearchStrategy.ENDS_WITH);
                assertThat(result).containsExactly(SearchCase.SERIES_3);
            }

            @Test
            void shouldNotFindMissingKey() {
                Set<SearchCase> result = index.search("missing", SearchIndex.SearchStrategy.ENDS_WITH);
                assertThat(result).isEmpty();
            }

            @Test
            void shouldFindDuplicates() {
                Set<SearchCase> result = index.search("duplicate", SearchIndex.SearchStrategy.ENDS_WITH);
                assertThat(result).containsExactlyInAnyOrderElementsOf(
                        List.of(SearchCase.DUPLICATE_1, SearchCase.DUPLICATE_2));
            }
        }

        @Nested
        class Contains {

            @Test
            void shouldNotFindEmptyKey() {
                Set<SearchCase> result = index.search("", SearchIndex.SearchStrategy.CONTAINS);
                assertThat(result).isEmpty();
            }

            @Test
            void shouldFindLeftPadded() {
                Set<SearchCase> result = index.search("left", SearchIndex.SearchStrategy.CONTAINS);
                assertThat(result).containsExactly(SearchCase.LEFT_PADDED);
            }

            @Test
            void shouldFindRightPadded() {
                Set<SearchCase> result = index.search("right", SearchIndex.SearchStrategy.CONTAINS);
                assertThat(result).containsExactly(SearchCase.RIGHT_PADDED);
            }

            @Test
            void shouldFindSpecialCharacter() {
                Set<SearchCase> result = index.search("äö", SearchIndex.SearchStrategy.CONTAINS);
                assertThat(result).containsExactly(SearchCase.SPECIAL_CHARACTER);
            }

            @Test
            void shouldFindNumbers() {
                Set<SearchCase> result = index.search("187", SearchIndex.SearchStrategy.CONTAINS);
                assertThat(result).containsExactly(SearchCase.NUMBERS);
            }

            @Test
            void shouldFindSeries() {
                Set<SearchCase> result = index.search("B", SearchIndex.SearchStrategy.CONTAINS);
                assertThat(result).containsExactlyInAnyOrder(SearchCase.SERIES_2, SearchCase.SERIES_3,
                        SearchCase.SERIES_4, SearchCase.SERIES_5);
            }

            @Test
            void shouldNotFindMissingKey() {
                Set<SearchCase> result = index.search("missing", SearchIndex.SearchStrategy.CONTAINS);
                assertThat(result).isEmpty();
            }

            @Test
            void shouldFindDuplicates() {
                Set<SearchCase> result = index.search("duplicate", SearchIndex.SearchStrategy.CONTAINS);
                assertThat(result).containsExactlyInAnyOrderElementsOf(
                        List.of(SearchCase.DUPLICATE_1, SearchCase.DUPLICATE_2));
            }
        }

        @Nested
        class Exact {

            @Test
            void shouldNotFindEmptyKey() {
                Set<SearchCase> result = index.search("", SearchIndex.SearchStrategy.EXACT);
                assertThat(result).isEmpty();
            }

            @Test
            void shouldFindLeftPadded() {
                Set<SearchCase> result = index.search("    left-padded", SearchIndex.SearchStrategy.EXACT);
                assertThat(result).containsExactly(SearchCase.LEFT_PADDED);
            }

            @Test
            void shouldFindRightPadded() {
                Set<SearchCase> result = index.search("right-padded    ", SearchIndex.SearchStrategy.EXACT);
                assertThat(result).containsExactly(SearchCase.RIGHT_PADDED);
            }

            @Test
            void shouldFindSpecialCharacter() {
                Set<SearchCase> result = index.search("$äö/üö)(", SearchIndex.SearchStrategy.EXACT);
                assertThat(result).containsExactly(SearchCase.SPECIAL_CHARACTER);
            }

            @Test
            void shouldFindNumbers() {
                Set<SearchCase> result = index.search("8187123", SearchIndex.SearchStrategy.EXACT);
                assertThat(result).containsExactly(SearchCase.NUMBERS);
            }

            @Test
            void shouldFindSeries() {
                Set<SearchCase> result = index.search("AAA", SearchIndex.SearchStrategy.EXACT);
                assertThat(result).containsExactly(SearchCase.SERIES_1);
            }

            @Test
            void shouldNotFindMissingKey() {
                Set<SearchCase> result = index.search("missing", SearchIndex.SearchStrategy.EXACT);
                assertThat(result).isEmpty();
            }

            @Test
            void shouldFindDuplicates() {
                Set<SearchCase> result = index.search("duplicate", SearchIndex.SearchStrategy.EXACT);
                assertThat(result).containsExactlyInAnyOrderElementsOf(
                        List.of(SearchCase.DUPLICATE_1, SearchCase.DUPLICATE_2));
            }
        }
    }
}
