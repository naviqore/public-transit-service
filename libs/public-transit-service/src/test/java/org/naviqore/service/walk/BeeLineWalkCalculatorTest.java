package org.naviqore.service.walk;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.naviqore.utils.spatial.GeoCoordinate;

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

        private static final double WALKING_SPEED = 1.4;

        @Test
        void expectedBehavior() {
            BeeLineWalkCalculator calculator = new BeeLineWalkCalculator(WALKING_SPEED);

            GeoCoordinate coordinate1 = getCoordinate1();
            GeoCoordinate coordinate2 = getCoordinate2();

            double distanceInMeters = coordinate1.distanceTo(
                    coordinate2) * BeeLineWalkCalculator.BEELINE_DISTANCE_FACTOR;
            double durationInSeconds = distanceInMeters / WALKING_SPEED;

            WalkCalculator.Walk walk = calculator.calculateWalk(coordinate1, coordinate2);

            assertEquals(walk.distance(), Math.round(distanceInMeters));
            assertEquals(walk.duration(), Math.round(durationInSeconds));
        }

        @Test
        void withDifferentWalkingSpeeds() {
            BeeLineWalkCalculator calculator1 = new BeeLineWalkCalculator(4000);
            BeeLineWalkCalculator calculator2 = new BeeLineWalkCalculator(2000);

            GeoCoordinate coordinate1 = getCoordinate1();
            GeoCoordinate coordinate2 = getCoordinate2();

            WalkCalculator.Walk walk1 = calculator1.calculateWalk(coordinate1, coordinate2);
            WalkCalculator.Walk walk2 = calculator2.calculateWalk(coordinate2, coordinate1);

            assertEquals(walk1.distance(), walk2.distance());
            // Need to accept +/-1 s difference, as rounding may introduce this inaccuracy
            assertTrue(Math.abs(walk1.duration() - 2 * walk2.duration()) <= 1, "Walk1 is not twice as fast as walk2");
        }

        @Test
        void withNullCoordinate_shouldThrowException() {
            BeeLineWalkCalculator calculator = new BeeLineWalkCalculator(4000);
            assertThrows(IllegalArgumentException.class, () -> calculator.calculateWalk(null, getCoordinate2()));
        }

        private GeoCoordinate getCoordinate1() {
            return new GeoCoordinate(47.37685009, 8.546391951);
        }

        private GeoCoordinate getCoordinate2() {
            return new GeoCoordinate(47.37652769, 8.544352776);
        }

    }

}
