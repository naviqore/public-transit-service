package org.naviqore.benchmark.timing;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RatioTest {

    @Test
    void equalVersionsDifferNotSignificantly() {
        Ratio ratio = Ratio.of(List.of(0.98, 1.02, 1.01, 0.99), List.of(1.01, 0.99, 1.0, 1.0));

        assertEquals(1.0, ratio.estimate(), 0.01);
        assertFalse(ratio.isSignificant());
        assertEquals("~", ratio.verdict());
    }

    @Test
    void aCandidateFasterInEveryRoundIsFaster() {
        Ratio ratio = Ratio.of(List.of(0.89, 0.91, 0.90, 0.90), List.of(0.90, 0.91, 0.89, 0.90));

        assertEquals(0.9, ratio.estimate(), 0.01);
        assertTrue(ratio.upper() < 1);
        assertEquals("faster", ratio.verdict());
    }

    @Test
    void theLoadOrderCancelsOut() {
        // the version loaded second runs 20 % slower in either order: the versions themselves are equally fast
        Ratio ratio = Ratio.of(Collections.nCopies(5, 1.2), Collections.nCopies(5, 1 / 1.2));

        assertEquals(1.0, ratio.estimate(), 1e-9);
    }

    @Test
    void oneOrderAloneIsTheRatio() {
        Ratio ratio = Ratio.of(List.of(1.1, 1.1, 1.1), List.of());

        assertEquals(1.1, ratio.estimate(), 1e-9);
        assertEquals("slower", ratio.verdict());
    }

    @Test
    void noRoundsAreNoRatio() {
        assertThrows(IllegalArgumentException.class, () -> Ratio.of(List.of(), List.of()));
    }

}
