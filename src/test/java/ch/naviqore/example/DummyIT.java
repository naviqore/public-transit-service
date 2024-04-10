package ch.naviqore.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dummy integration test
 *
 * @author munterfi
 */
class DummyIT {

    private Container container;
    private Dummy dummy;

    @BeforeEach
    void setUp() {
        container = new Container();
        dummy = new Dummy(container);
    }

    @Test
    void add() {
        int initial = container.getValue();
        int result = dummy.add(10);

        assertThat(result).isEqualTo(initial + 10);
    }
}