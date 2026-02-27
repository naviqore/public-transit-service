package org.naviqore.utils.spatial;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class GeoCoordinateTest {

    private static final double TOLERANCE = 0.0001;

    @Nested
    class Constructor {
        static Stream<Arguments> invalidCoordinatesProvider() {
            return Stream.of(Arguments.of(-91, 0), Arguments.of(91, 0), Arguments.of(0, -181), Arguments.of(0, 181),
                    Arguments.of(Double.NaN, 0), Arguments.of(0, Double.NaN));
        }

        @ParameterizedTest
        @MethodSource("invalidCoordinatesProvider")
        void invalidArguments(double latitude, double longitude) {
            assertThrows(IllegalArgumentException.class, () -> new GeoCoordinate(latitude, longitude));
        }
    }

    @Nested
    class DistanceTo {

        static Stream<Arguments> distanceToProvider() {
            // city coordinates and distances from
            // https://www.luftlinie.org/
            GeoCoordinate zurich = new GeoCoordinate(47.369022, 8.538033);
            GeoCoordinate bern = new GeoCoordinate(46.948090, 7.447440);
            GeoCoordinate newYork = new GeoCoordinate(40.714270, -74.005970);
            GeoCoordinate sydney = new GeoCoordinate(-33.867138, 151.207108);
            // distance calculations from
            // https://www.vcalc.com/wiki/vCalc/Haversine%20-%20Distance
            GeoCoordinate primeMeridian = new GeoCoordinate(0, 0);
            GeoCoordinate northPole = new GeoCoordinate(90, 0);
            GeoCoordinate tenNorth = new GeoCoordinate(10, 0);
            GeoCoordinate tenEast = new GeoCoordinate(0, 10);
            GeoCoordinate hundredEightyEast = new GeoCoordinate(0, 180);
            GeoCoordinate southPole = new GeoCoordinate(-90, 0);

            return Stream.of(Arguments.of(zurich, "Zürich", zurich, "Zürich", 0),
                    Arguments.of(zurich, "Zürich", bern, "Bern", 94_820),
                    Arguments.of(zurich, "Zürich", newYork, "New York", 6_323_800),
                    Arguments.of(zurich, "Zürich", sydney, "Sidney", 16_567_980),
                    Arguments.of(primeMeridian, "Prime Meridian", hundredEightyEast, "180° East", 20_015_120),
                    Arguments.of(northPole, "North Pole", southPole, "South Pole", 20_015_120),
                    Arguments.of(northPole, "North Pole", primeMeridian, "Equator", 10_007_560),
                    Arguments.of(primeMeridian, "Equator", tenNorth, "10° North", 1_111_950),
                    Arguments.of(primeMeridian, "Prime Meridian", tenEast, "10° East", 1_111_950));
        }

        static Stream<Arguments> invalidCoordinatesProvider() {
            return Stream.of(Arguments.of(-91, 0), Arguments.of(91, 0), Arguments.of(0, -181), Arguments.of(0, 181),
                    Arguments.of(Double.NaN, 0), Arguments.of(0, Double.NaN));
        }

        @ParameterizedTest(name = "distance from: {1} to: {3}")
        @MethodSource("distanceToProvider")
        void distanceTo(GeoCoordinate c1, String d1, GeoCoordinate c2, String d2, double expected) {
            String msg = String.format("distance from %s to %s should be %f", d1, d2, expected);
            assertEquals(expected, c1.distanceTo(c2), expected * TOLERANCE, msg);
        }

        @Test
        void nullArgument() {
            GeoCoordinate c = new GeoCoordinate(47.368650, 8.539183);
            assertThrows(IllegalArgumentException.class, () -> c.distanceTo(null));
        }

        @ParameterizedTest
        @MethodSource("invalidCoordinatesProvider")
        void invalidArguments(double latitude, double longitude) {
            GeoCoordinate c = new GeoCoordinate(47.368650, 8.539183);
            assertThrows(IllegalArgumentException.class, () -> c.distanceTo(latitude, longitude));
        }

        @ParameterizedTest(name = "distance from: {1} to: {3}")
        @MethodSource("distanceToProvider")
        void withDoubleCoordinates(GeoCoordinate c1, String d1, GeoCoordinate c2, String d2, double expected) {
            String msg = String.format("distance from %s to %s should be %f", d1, d2, expected);
            assertEquals(expected, c1.distanceTo(c2.latitude(), c2.longitude()), expected * TOLERANCE, msg);
        }

        @Test
        void withDifferentType() {
            GeoCoordinate c = new GeoCoordinate(47.368650, 8.539183);
            CartesianCoordinate cc = new CartesianCoordinate(0, 0);
            assertThrows(IllegalArgumentException.class, () -> c.distanceTo(cc));
        }

    }

    @Nested
    class CompareTo {

        static Stream<Arguments> compareToArguments() {
            // allowed discrepancy between coordinates
            double epsilon = 1e-5;
            return Stream.of(
                    // Equal cases
                    Arguments.of(new GeoCoordinate(0, 0), new GeoCoordinate(0, 0), 0),
                    Arguments.of(new GeoCoordinate(0, 0), new GeoCoordinate(0, -epsilon), 0),
                    Arguments.of(new GeoCoordinate(0, 0), new GeoCoordinate(0, epsilon), 0),
                    Arguments.of(new GeoCoordinate(0, 0), new GeoCoordinate(epsilon, 0), 0),
                    Arguments.of(new GeoCoordinate(0, 0), new GeoCoordinate(-epsilon, 0), 0),
                    // Greater than cases
                    Arguments.of(new GeoCoordinate(0, 0), new GeoCoordinate(0, -1), 1),
                    Arguments.of(new GeoCoordinate(0, 0), new GeoCoordinate(-1, 0), 1),
                    Arguments.of(new GeoCoordinate(0, 0), new GeoCoordinate(-1, 1), 1),
                    // Smaller than cases
                    Arguments.of(new GeoCoordinate(0, 0), new GeoCoordinate(0, 1), -1),
                    Arguments.of(new GeoCoordinate(0, 0), new GeoCoordinate(1, 0), -1),
                    Arguments.of(new GeoCoordinate(0, 0), new GeoCoordinate(1, -1), -1));
        }

        @ParameterizedTest(name = "compare {0} to {1}")
        @MethodSource("compareToArguments")
        void compareTo(GeoCoordinate c1, GeoCoordinate c2, double expected) {
            assertEquals(expected, c1.compareTo(c2));
        }

    }

}
