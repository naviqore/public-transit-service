package ch.naviqore.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Dummy unit test
 *
 * @author munterfi
 */
@ExtendWith(MockitoExtension.class)
class DummyTest {

    @Mock
    private Container container;

    @InjectMocks
    private Dummy dummy;

    @Test
    void add() {
        // mock getter of container
        when(container.getValue()).thenReturn(10);

        // act
        int result = dummy.add(5);

        // verify container.setValue(15) is called
        verify(container, times(1)).getValue();
        verify(container, times(1)).setValue(15);
        assertThat(result).isEqualTo(15);
    }
}