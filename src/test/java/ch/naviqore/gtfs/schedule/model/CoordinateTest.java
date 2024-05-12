package ch.naviqore.gtfs.schedule.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class CoordinateTest {

    @Nested
    class Constructor {
        @ParameterizedTest
        @MethodSource("invalidCoordinatesProvider")
        void invalidArguments(double latitude, double longitude) {
            assertThrows(IllegalArgumentException.class, () -> new Coordinate(latitude, longitude));
        }

        static Stream<Arguments> invalidCoordinatesProvider() {
            return Stream.of(Arguments.of(-91, 0), Arguments.of(91, 0), Arguments.of(0, -181), Arguments.of(0, 181));
        }
    }

    @Nested
    class DistanceTo {

        @ParameterizedTest(name = "distance from: {1} to: {3}")
        @MethodSource("distanceToProvider")
        void distanceTo(Coordinate c1, String d1, Coordinate c2, String d2, double expected) {
            String msg = String.format("distance from %s to %s should be %f", d1, d2, expected);
            assertEquals(expected, c1.distanceTo(c2), expected * 0.0001, msg);
        }

        static Stream<Arguments> distanceToProvider() {

            // city coordinates and distances from
            // https://www.luftlinie.org/
            Coordinate zuerich = new Coordinate(47.369022, 8.538033);
            Coordinate bern = new Coordinate(46.948090,7.447440);
            Coordinate newYork = new Coordinate(40.714270,-74.005970);
            Coordinate sydney = new Coordinate(-33.867138,151.207108);
            // distance calculations from
            // https://www.vcalc.com/wiki/vCalc/Haversine%20-%20Distance
            Coordinate primeMeridian = new Coordinate(0, 0);
            Coordinate northPole = new Coordinate(90, 0);
            Coordinate tenNorth = new Coordinate(10, 0);
            Coordinate tenEast = new Coordinate(0, 10);
            Coordinate hundredEightyEast = new Coordinate(0, 180);
            Coordinate southPole = new Coordinate(-90, 0);

            return Stream.of(
                    Arguments.of(zuerich, "Zürich", zuerich, "Zürich", 0),
                    Arguments.of(zuerich, "Zürich", bern, "Bern", 94_820),
                    Arguments.of(zuerich, "Zürich", newYork, "New York", 6_323_800),
                    Arguments.of(zuerich, "Zürich", sydney, "Sidney", 16_567_980),
                    Arguments.of(primeMeridian, "Prime Meridian", hundredEightyEast, "180° East", 20_015_120),
                    Arguments.of(northPole, "North Pole", southPole, "South Pole", 20_015_120),
                    Arguments.of(northPole, "North Pole", primeMeridian, "Equator", 10_007_560),
                    Arguments.of(primeMeridian, "Equator", tenNorth, "10° North", 1_111_950),
                    Arguments.of(primeMeridian, "Prime Meridian", tenEast, "10° East", 1_111_950)
            );
        }

        @Test
        void nullArgument() {
            Coordinate c = new Coordinate(47.368650, 8.539183);
            assertThrows(IllegalArgumentException.class, () -> c.distanceTo(null));
        }

        @ParameterizedTest
        @MethodSource("invalidCoordinatesProvider")
        void invalidArguments(double latitude, double longitude) {
            Coordinate c = new Coordinate(47.368650, 8.539183);
            assertThrows(IllegalArgumentException.class, () -> c.distanceTo(latitude, longitude));
        }

        @ParameterizedTest(name = "distance from: {1} to: {3}")
        @MethodSource("distanceToProvider")
        void withDoubleCoordinates(Coordinate c1, String d1, Coordinate c2, String d2, double expected) {
            String msg = String.format("distance from %s to %s should be %f", d1, d2, expected);
            assertEquals(expected, c1.distanceTo(c2.getLatitude(), c2.getLongitude()), expected * 0.0001, msg);
        }

        static Stream<Arguments> invalidCoordinatesProvider() {
            return Stream.of(Arguments.of(-91, 0), Arguments.of(91, 0), Arguments.of(0, -181), Arguments.of(0, 181));
        }

    }

}
