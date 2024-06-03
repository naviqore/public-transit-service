package ch.naviqore.service.walk;

import ch.naviqore.utils.spatial.GeoCoordinate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BeeLineWalkCalculatorTest {

    @Nested
    class Constructor {

        @Test
        void withValidWalkingSpeed() {
            assertDoesNotThrow(() -> new BeeLineWalkCalculator(1000));
        }

        @Test
        void shouldCreateExceptionForNegativeWalkingSpeed() {
            assertThrows(IllegalArgumentException.class, () -> new BeeLineWalkCalculator(-1));
        }

        @Test
        void shouldCreateExceptionForZeroWalkingSpeed() {
            assertThrows(IllegalArgumentException.class, () -> new BeeLineWalkCalculator(0));
        }

    }

    @Nested
    class CalculateWalk {

        private GeoCoordinate coordinate1;
        private GeoCoordinate coordinate2;

        @BeforeEach
        void setUp() {
            coordinate1 = new GeoCoordinate(47.37685009, 8.546391951);
            coordinate2 = new GeoCoordinate(47.37652769, 8.544352776);

        }

        @Test
        void expectedBehavior() {
            int walkingSpeed = 3600; // 1 m/s
            BeeLineWalkCalculator calculator = new BeeLineWalkCalculator(walkingSpeed);

            double distanceInMeters = coordinate1.distanceTo(coordinate2);
            int durationInSeconds = (int) distanceInMeters;

            WalkCalculator.Walk walk = calculator.calculateWalk(coordinate1, coordinate2);

            assertEquals(walk.distance(), Math.round(distanceInMeters));
            assertEquals(walk.duration(), durationInSeconds);
        }

        @Test
        void withDifferentWalkingSpeeds() {
            BeeLineWalkCalculator calculator1 = new BeeLineWalkCalculator(4000);
            BeeLineWalkCalculator calculator2 = new BeeLineWalkCalculator(2000);

            WalkCalculator.Walk walk1 = calculator1.calculateWalk(coordinate1, coordinate2);
            WalkCalculator.Walk walk2 = calculator2.calculateWalk(coordinate2, coordinate1);

            assertEquals(walk1.distance(), walk2.distance());
            // need to accept +/-1 s difference, as rounding may introduce this inaccuracy
            assertEquals(2 * walk2.duration(), walk1.duration(), 1, "Walk1 is not twice as fast as walk2");
        }

        @Test
        void withNullCoordinate_shouldThrowException() {
            BeeLineWalkCalculator calculator = new BeeLineWalkCalculator(4000);
            assertThrows(IllegalArgumentException.class, () -> calculator.calculateWalk(null, coordinate2));
        }

    }

}
