package org.naviqore.utils.spatial.index;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.naviqore.utils.spatial.CartesianCoordinate;

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

}