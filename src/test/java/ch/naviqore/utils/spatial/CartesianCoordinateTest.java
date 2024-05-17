package ch.naviqore.utils.spatial;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CartesianCoordinateTest {

    private static final double TOLERANCE = 0.0001;

    @Nested
    class Constructor {
        // Note: All positive test cases are covered in the distanceTo test cases
        static Stream<Arguments> invalidCoordinatesProvider() {
            return Stream.of(Arguments.of(Double.NaN, 0), Arguments.of(0, Double.NaN));
        }

        @ParameterizedTest
        @MethodSource("invalidCoordinatesProvider")
        void invalidArguments(double x, double y) {
            assertThrows(IllegalArgumentException.class, () -> new CartesianCoordinate(x, y));
        }
    }

    @Nested
    class DistanceTo {

        static Stream<Arguments> distanceToProvider() {
            return Stream.of(Arguments.of(new CartesianCoordinate(0, 0), new CartesianCoordinate(0, 0), 0),
                    Arguments.of(new CartesianCoordinate(0, 0), new CartesianCoordinate(1, 0), 1),
                    Arguments.of(new CartesianCoordinate(0, 0), new CartesianCoordinate(0, 1), 1),
                    Arguments.of(new CartesianCoordinate(0, 0), new CartesianCoordinate(1, 1), Math.sqrt(2)),
                    Arguments.of(new CartesianCoordinate(0, 0), new CartesianCoordinate(-1, 0), 1),
                    Arguments.of(new CartesianCoordinate(0, 0), new CartesianCoordinate(0, -1), 1),
                    Arguments.of(new CartesianCoordinate(0, 0), new CartesianCoordinate(-1, -1), Math.sqrt(2)),
                    Arguments.of(new CartesianCoordinate(1, 1), new CartesianCoordinate(1, 1), 0),
                    Arguments.of(new CartesianCoordinate(1, 1), new CartesianCoordinate(2, 1), 1),
                    Arguments.of(new CartesianCoordinate(1, 1), new CartesianCoordinate(1, 2), 1),
                    Arguments.of(new CartesianCoordinate(1.5, 1.5), new CartesianCoordinate(1.5, 1.5), 0),
                    Arguments.of(new CartesianCoordinate(1.5, 1.5), new CartesianCoordinate(2.5, 1.5), 1),
                    Arguments.of(new CartesianCoordinate(1, 1), new CartesianCoordinate(-1, -1), 2 * Math.sqrt(2)));
        }

        @ParameterizedTest(name = "distance from: {0} to: {1}")
        @MethodSource("distanceToProvider")
        void distanceTo(CartesianCoordinate coordinate1, CartesianCoordinate coordinate2, double expectedDistance) {
            assertEquals(expectedDistance, coordinate1.distanceTo(coordinate2), TOLERANCE);
        }

        @ParameterizedTest(name = "distance from: {0} to: {1}")
        @MethodSource("distanceToProvider")
        void distanceTo_withDoubleParameters(CartesianCoordinate coordinate1, CartesianCoordinate coordinate2,
                                             double expectedDistance) {
            assertEquals(expectedDistance,
                    coordinate1.distanceTo(coordinate2.getFirstComponent(), coordinate2.getSecondComponent()),
                    TOLERANCE);
        }

        @Test
        void distanceTo_withNull() {
            CartesianCoordinate coordinate = new CartesianCoordinate(0, 0);
            assertThrows(IllegalArgumentException.class, () -> coordinate.distanceTo(null));
        }

        @Test
        void distanceTo_withDifferentType() {
            CartesianCoordinate coordinate = new CartesianCoordinate(0, 0);
            GeoCoordinate geoCoordinate = new GeoCoordinate(0, 0);
            assertThrows(IllegalArgumentException.class, () -> coordinate.distanceTo(geoCoordinate));
        }

        @Test
        void distanceTo_withNaN() {
            CartesianCoordinate coordinate = new CartesianCoordinate(0, 0);
            assertThrows(IllegalArgumentException.class, () -> coordinate.distanceTo(Double.NaN, 0));
            assertThrows(IllegalArgumentException.class, () -> coordinate.distanceTo(0, Double.NaN));
        }

    }

}
