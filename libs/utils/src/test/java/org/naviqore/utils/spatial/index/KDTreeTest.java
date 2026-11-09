package org.naviqore.utils.spatial.index;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.naviqore.utils.spatial.CartesianCoordinate;
import org.naviqore.utils.spatial.GeoCoordinate;
import org.naviqore.utils.spatial.Location;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class KDTreeTest {
    static final TestFacility vilnius = new TestFacility("Vilnius", new CartesianCoordinate(54.68916, 25.2798));
    static final TestFacility munich = new TestFacility("Munich", new CartesianCoordinate(48.137154, 11.576124));
    static final TestFacility augsburg = new TestFacility("Augsburg", new CartesianCoordinate(48.370544, 10.89779));
    static final TestFacility berlin = new TestFacility("Berlin", new CartesianCoordinate(52.520008, 13.404954));
    static final TestFacility frankfurt = new TestFacility("Frankfurt", new CartesianCoordinate(50.110924, 8.682127));
    static final TestFacility zurich = new TestFacility("Zurich", new CartesianCoordinate(47.3769, 8.5417));
    static final TestFacility militarkantine = new TestFacility("Militarkantine",
            new CartesianCoordinate(47.42100820116168, 9.35977158264066));
    static final TestFacility sportsFacilityKreuzbleiche = new TestFacility("Sportanlage Kreuzbleiche",
            new CartesianCoordinate(47.41984757221546, 9.361976306041305));
    static final TestFacility parkingKreuzbleiche = new TestFacility("Parkgarage Kreuzbleiche",
            new CartesianCoordinate(47.4202611944959, 9.362182342510467));

    private KDTree<TestFacility> buildTestKDTree() {
        // create some locations with near and far distances
        KDTree<TestFacility> kdTree = new KDTree<>();
        kdTree.insert(munich);
        kdTree.insert(berlin);
        kdTree.insert(frankfurt);
        kdTree.insert(zurich);
        kdTree.insert(militarkantine);
        kdTree.insert(sportsFacilityKreuzbleiche);
        return kdTree;
    }

    /**
     * Where the coordinates of a test lie: anywhere, near the poles, along the antimeridian or within a country.
     */
    enum Region {
        EARTH(random -> new GeoCoordinate(random.nextDouble(-90, 90), random.nextDouble(-180, 180))),
        POLES(random -> new GeoCoordinate((random.nextBoolean() ? 1 : -1) * random.nextDouble(80, 90),
                random.nextDouble(-180, 180))),
        ANTIMERIDIAN(random -> new GeoCoordinate(random.nextDouble(-60, 60),
                (random.nextBoolean() ? 1 : -1) * random.nextDouble(170, 180))),
        SWITZERLAND(random -> new GeoCoordinate(random.nextDouble(45.8, 47.8), random.nextDouble(5.9, 10.5)));

        private final Function<Random, GeoCoordinate> draw;

        Region(Function<Random, GeoCoordinate> draw) {
            this.draw = draw;
        }

        GeoCoordinate draw(Random random) {
            return draw.apply(random);
        }
    }

    record Place(int id, GeoCoordinate coordinate) implements Location<GeoCoordinate> {

        @Override
        public GeoCoordinate getCoordinate() {
            return coordinate;
        }
    }

    /**
     * A place that counts how often its coordinate is read: a few times for every place a search visits.
     */
    record CountedPlace(GeoCoordinate coordinate, AtomicInteger reads) implements Location<GeoCoordinate> {

        @Override
        public GeoCoordinate getCoordinate() {
            reads.incrementAndGet();
            return coordinate;
        }
    }

    @Nested
    class Insert {
        @Test
        void insert() {
            KDTree<TestFacility> kdTree = new KDTree<>();
            // confirm that the tree is empty
            Assertions.assertNull(kdTree.root);

            // insert a location, will be root
            kdTree.insert(berlin);
            Assertions.assertEquals(berlin, kdTree.root.getLocation());
            Assertions.assertNull(kdTree.root.getLeft());
            Assertions.assertNull(kdTree.root.getRight());

            // insert a location with a smaller latitude coordinate (should go left)
            kdTree.insert(augsburg);
            Assertions.assertEquals(berlin, kdTree.root.getLocation());
            Assertions.assertEquals(augsburg, kdTree.root.getLeft().getLocation());
            Assertions.assertNull(kdTree.root.getRight());
            Assertions.assertNull(kdTree.root.getLeft().getLeft());
            Assertions.assertNull(kdTree.root.getLeft().getRight());

            // insert a location with a smaller latitude than berlin and augsburg (but larger longitude than augsburg)
            // --> should go left and then right
            kdTree.insert(munich);
            Assertions.assertEquals(berlin, kdTree.root.getLocation());
            Assertions.assertEquals(augsburg, kdTree.root.getLeft().getLocation());
            Assertions.assertEquals(munich, kdTree.root.getLeft().getRight().getLocation());
            Assertions.assertNull(kdTree.root.getRight());
            Assertions.assertNull(kdTree.root.getLeft().getLeft());
            Assertions.assertNull(kdTree.root.getLeft().getRight().getLeft());
            Assertions.assertNull(kdTree.root.getLeft().getRight().getRight());
            // insert a location where latitude is greater than root (should go right)
            kdTree.insert(vilnius);
            Assertions.assertEquals(berlin, kdTree.root.getLocation());
            Assertions.assertEquals(augsburg, kdTree.root.getLeft().getLocation());
            Assertions.assertEquals(munich, kdTree.root.getLeft().getRight().getLocation());
            Assertions.assertEquals(vilnius, kdTree.root.getRight().getLocation());
            Assertions.assertNull(kdTree.root.getLeft().getLeft());
            Assertions.assertNull(kdTree.root.getLeft().getRight().getLeft());
            Assertions.assertNull(kdTree.root.getLeft().getRight().getRight());
            Assertions.assertNull(kdTree.root.getRight().getLeft());
            Assertions.assertNull(kdTree.root.getRight().getRight());
        }

        @Test
        void insert_withNull() {
            KDTree<TestFacility> kdTree = new KDTree<>();
            Assertions.assertThrows(IllegalArgumentException.class, () -> kdTree.insert(null));
        }
    }

    @Nested
    class NearestNeighbour {
        private static Stream<Object[]> provideTestCases() {
            return Stream.of(new Object[]{parkingKreuzbleiche, sportsFacilityKreuzbleiche},
                    new Object[]{augsburg, munich});
        }

        @ParameterizedTest
        @MethodSource("provideTestCases")
        void nearestNeighbour(TestFacility location, TestFacility expectedNearestLocation) {
            KDTree<TestFacility> kdTree = buildTestKDTree();
            Assertions.assertEquals(expectedNearestLocation, kdTree.nearestNeighbor(location));
        }

        @Test
        void nearestNeighbour_withNull() {
            KDTree<TestFacility> kdTree = buildTestKDTree();
            Assertions.assertThrows(IllegalArgumentException.class, () -> kdTree.nearestNeighbor((TestFacility) null));
        }

        @Test
        void nearestNeighbour_withEmptyTree() {
            KDTree<TestFacility> kdTree = new KDTree<>();
            Assertions.assertThrows(IllegalStateException.class, () -> kdTree.nearestNeighbor(munich));
        }
    }

    @Nested
    class RangeSearch {
        @Test
        void rangeSearch() {
            KDTree<TestFacility> kdTree = buildTestKDTree();

            // Note: because of x,y coordinate usage in the CartesianCoordinate class, the distance is calculated as
            // sqrt((x1-x2)^2 + (y1-y2)^2) --> and 1° latitude/longitude are treated as 1 km

            // includes nothing
            Assertions.assertEquals(0, kdTree.rangeSearch(parkingKreuzbleiche, 0.0001).size());
            // includes sportsFacilityKreuzbleiche
            Assertions.assertEquals(1, kdTree.rangeSearch(parkingKreuzbleiche, 0.001).size());
            // adds sportsFacilityKreuzbleiche and militarkantine
            Assertions.assertEquals(2, kdTree.rangeSearch(parkingKreuzbleiche, 0.01).size());
            // adds Zurich
            Assertions.assertEquals(3, kdTree.rangeSearch(parkingKreuzbleiche, 1).size());
        }

        @Test
        void rangeSearch_withCoordinateArgument() {
            KDTree<TestFacility> kdTree = buildTestKDTree();

            // includes all nodes in tree
            Assertions.assertEquals(6, kdTree.rangeSearch(parkingKreuzbleiche.getCoordinate(), 7).size());
        }

        @Test
        void rangeSearch_withDoubleArguments() {
            KDTree<TestFacility> kdTree = buildTestKDTree();
            double x = parkingKreuzbleiche.getCoordinate().getFirstComponent();
            double y = parkingKreuzbleiche.getCoordinate().getSecondComponent();
            // includes all nodes in tree except Berlin (6.5 "km" away)
            Assertions.assertEquals(5, kdTree.rangeSearch(x, y, 3).size());
        }

        @Test
        void rangeSearch_withNegativeDistance() {
            KDTree<TestFacility> kdTree = buildTestKDTree();
            Assertions.assertThrows(IllegalArgumentException.class, () -> kdTree.rangeSearch(parkingKreuzbleiche, -1));
        }

        @Test
        void rangeSearch_withZeroDistance() {
            KDTree<TestFacility> kdTree = buildTestKDTree();
            Assertions.assertThrows(IllegalArgumentException.class, () -> kdTree.rangeSearch(parkingKreuzbleiche, 0));
        }

        @Test
        void rangeSearch_withNull() {
            KDTree<TestFacility> kdTree = buildTestKDTree();
            Assertions.assertThrows(IllegalArgumentException.class, () -> kdTree.rangeSearch((TestFacility) null, 500));
        }

        @Test
        void rangeSearch_withEmptyTree() {
            KDTree<TestFacility> kdTree = new KDTree<>();
            Assertions.assertThrows(IllegalStateException.class, () -> kdTree.rangeSearch(munich, 500));
        }
    }

    @Nested
    class GeographicCoordinates {

        private static final int PLACES = 2_000;
        private static final int QUERIES = 100;
        private static final double[] RADII = {100, 500, 5_000, 50_000, 500_000, 5_000_000};
        // the poles and the antimeridian, and places a few meters apart across them, as places and as queries
        private static final List<GeoCoordinate> EDGES = List.of(new GeoCoordinate(90, 0), new GeoCoordinate(-90, 0),
                new GeoCoordinate(89.9999, 0), new GeoCoordinate(89.9999, 170), new GeoCoordinate(0, 180),
                new GeoCoordinate(0, -180), new GeoCoordinate(45, 179.99995), new GeoCoordinate(45, -179.99995));

        private static List<Place> places(Region region, Random random) {
            List<Place> places = new ArrayList<>();
            for (GeoCoordinate edge : EDGES) {
                places.add(new Place(places.size(), edge));
            }
            while (places.size() < PLACES) {
                places.add(new Place(places.size(), region.draw(random)));
            }

            return places;
        }

        private static List<GeoCoordinate> queries(Region region, Random random) {
            List<GeoCoordinate> queries = new ArrayList<>(EDGES);
            while (queries.size() < QUERIES) {
                queries.add(region.draw(random));
            }

            return queries;
        }

        @ParameterizedTest
        @EnumSource(Region.class)
        void rangeSearch_findsWhatAFullScanFinds(Region region) {
            Random random = new Random(42);
            List<Place> places = places(region, random);
            KDTree<Place> kdTree = new KDTreeBuilder<Place>().addLocations(places).build();

            for (GeoCoordinate query : queries(region, random)) {
                for (double radius : RADII) {
                    Set<Place> expected = places.stream()
                            .filter(place -> place.getCoordinate()
                                    .distanceTo(query.latitude(), query.longitude()) <= radius)
                            .collect(Collectors.toSet());
                    Assertions.assertEquals(expected, new HashSet<>(kdTree.rangeSearch(query, radius)),
                            query + ", radius " + radius);
                }
            }
        }

        @ParameterizedTest
        @EnumSource(Region.class)
        void nearestNeighbor_findsWhatAFullScanFinds(Region region) {
            Random random = new Random(42);
            List<Place> places = places(region, random);
            KDTree<Place> kdTree = new KDTreeBuilder<Place>().addLocations(places).build();

            for (GeoCoordinate query : queries(region, random)) {
                Place expected = places.stream()
                        .min(Comparator.comparingDouble(
                                place -> place.getCoordinate().distanceTo(query.latitude(), query.longitude())))
                        .orElseThrow();
                Assertions.assertEquals(expected, kdTree.nearestNeighbor(query), query.toString());
            }
        }

        @Test
        void searches_visitAFewPlacesInsteadOfAll() {
            Random random = new Random(42);
            AtomicInteger reads = new AtomicInteger();
            List<CountedPlace> places = new ArrayList<>();
            for (int i = 0; i < 10_000; i++) {
                places.add(new CountedPlace(Region.SWITZERLAND.draw(random), reads));
            }
            KDTree<CountedPlace> kdTree = new KDTreeBuilder<CountedPlace>().addLocations(places).build();

            reads.set(0);
            int searches = 0;
            for (int i = 0; i < QUERIES; i++) {
                GeoCoordinate query = Region.SWITZERLAND.draw(random);
                kdTree.rangeSearch(query.latitude(), query.longitude(), 500);
                kdTree.nearestNeighbor(query.latitude(), query.longitude());
                searches += 2;
            }

            // a search that cannot skip a side visits every place, 10 000 per search
            int readsPerSearch = reads.get() / searches;
            Assertions.assertTrue(readsPerSearch < 300, readsPerSearch + " reads of a coordinate per search");
        }
    }

}