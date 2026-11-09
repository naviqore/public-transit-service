package org.naviqore.utils.spatial.index;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.naviqore.utils.spatial.CartesianCoordinate;
import org.naviqore.utils.spatial.Coordinate;
import org.naviqore.utils.spatial.GeoCoordinate;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SplitDistanceTest {

    private static final double PRECISION = 1e-6;

    @Test
    void unknownCoordinateType_hasNoBound() {
        Coordinate unknown = new Coordinate() {
            @Override
            public double getFirstComponent() {
                return 3;
            }

            @Override
            public double getSecondComponent() {
                return 4;
            }

            @Override
            public double distanceTo(Coordinate other) {
                return distanceTo(other.getFirstComponent(), other.getSecondComponent());
            }

            @Override
            public double distanceTo(double firstComponent, double secondComponent) {
                return 0;
            }
        };

        assertEquals(0, SplitDistance.toOtherSide(unknown, Coordinate.Axis.FIRST, 1, 10));
    }

    @Nested
    class Cartesian {

        @Test
        void isTheDifferenceOfTheComponentsOnTheAxis() {
            CartesianCoordinate split = new CartesianCoordinate(3, 4);

            assertEquals(2, SplitDistance.toOtherSide(split, Coordinate.Axis.FIRST, 1, 10), PRECISION);
            assertEquals(6, SplitDistance.toOtherSide(split, Coordinate.Axis.SECOND, 1, 10), PRECISION);
        }
    }

    @Nested
    class Geographic {

        // the length of a degree on a great circle
        private static final double DEGREE = GeoCoordinate.EARTH_RADIUS * Math.PI / 180;

        // anywhere, near the poles or along the antimeridian
        private static GeoCoordinate draw(Random random) {
            double sign = random.nextBoolean() ? 1 : -1;
            return switch (random.nextInt(3)) {
                case 0 -> new GeoCoordinate(random.nextDouble(-90, 90), random.nextDouble(-180, 180));
                case 1 -> new GeoCoordinate(sign * random.nextDouble(80, 90), random.nextDouble(-180, 180));
                default -> new GeoCoordinate(random.nextDouble(-90, 90), sign * random.nextDouble(170, 180));
            };
        }

        @Test
        void latitude_isTheDistanceToTheParallel() {
            GeoCoordinate split = new GeoCoordinate(47, 8);
            double distance = SplitDistance.toOtherSide(split, Coordinate.Axis.FIRST, 46, 10);

            assertEquals(DEGREE, distance, PRECISION);
            assertEquals(new GeoCoordinate(46, 10).distanceTo(47, 10), distance, PRECISION);
        }

        @Test
        void longitude_isTheDistanceToTheMeridian() {
            GeoCoordinate split = new GeoCoordinate(0, 10);

            assertEquals(DEGREE, SplitDistance.toOtherSide(split, Coordinate.Axis.SECOND, 0, 9), PRECISION);
            // at 60° of latitude, the meridians are half as far apart as at the equator
            assertEquals(GeoCoordinate.EARTH_RADIUS * Math.asin(0.5 * Math.sin(Math.toRadians(1))),
                    SplitDistance.toOtherSide(split, Coordinate.Axis.SECOND, 60, 9), PRECISION);
        }

        @Test
        void longitude_acrossTheAntimeridian() {
            // the other side of -179° from 179.9° begins at -180°, a tenth of a degree away
            assertEquals(0.1 * DEGREE,
                    SplitDistance.toOtherSide(new GeoCoordinate(0, -179), Coordinate.Axis.SECOND, 0, 179.9), PRECISION);
            assertEquals(0.1 * DEGREE,
                    SplitDistance.toOtherSide(new GeoCoordinate(0, 179), Coordinate.Axis.SECOND, 0, -179.9), PRECISION);
        }

        @Test
        void longitude_moreThan90DegreesAway_isTheDistanceToThePole() {
            // the other side of -170° from 0° lies between -180° and -170°, and its nearest coordinate is the pole
            assertEquals(new GeoCoordinate(45, 0).distanceTo(90, 0),
                    SplitDistance.toOtherSide(new GeoCoordinate(0, -170), Coordinate.Axis.SECOND, 45, 0), PRECISION);
        }

        @Test
        void isALowerBoundOfTheDistanceToEveryCoordinateOnTheOtherSide() {
            Random random = new Random(42);
            for (int i = 0; i < 100_000; i++) {
                GeoCoordinate split = draw(random);
                GeoCoordinate point = draw(random);
                GeoCoordinate other = draw(random);
                for (Coordinate.Axis axis : Coordinate.Axis.values()) {
                    boolean pointBelow = point.getComponent(axis) < split.getComponent(axis);
                    boolean otherBelow = other.getComponent(axis) < split.getComponent(axis);
                    if (pointBelow != otherBelow) {
                        double bound = SplitDistance.toOtherSide(split, axis, point.latitude(), point.longitude());
                        double distance = other.distanceTo(point.latitude(), point.longitude());
                        assertTrue(bound <= distance,
                                String.format("%s split at %s: %f to the other side, but %s is %f from %s", axis, split,
                                        bound, other, distance, point));
                    }
                }
            }
        }
    }

}
