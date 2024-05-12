package ch.naviqore.utils.spatial;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class KDTreeTest {
    static MockFacility vilnius = new MockFacility("Vilnius", new MockCoordinate(54.68916, 25.2798));
    static MockFacility munich = new MockFacility("Munich", new MockCoordinate(48.137154, 11.576124));
    static MockFacility augsburg = new MockFacility("Augsburg", new MockCoordinate(48.370544, 10.89779));
    static MockFacility berlin = new MockFacility("Berlin", new MockCoordinate(52.520008, 13.404954));
    static MockFacility frankfurt = new MockFacility("Frankfurt", new MockCoordinate(50.110924, 8.682127));
    static MockFacility zurich = new MockFacility("Zurich", new MockCoordinate(47.3769, 8.5417));
    static MockFacility militarkantine = new MockFacility("Militarkantine",
            new MockCoordinate(47.42100820116168, 9.35977158264066));
    static MockFacility sportsFacilityKreuzbleiche = new MockFacility("Sportanlage Kreuzbleiche",
            new MockCoordinate(47.41984757221546, 9.361976306041305));
    static MockFacility parkingKreuzbleiche = new MockFacility("Parkgarage Kreuzbleiche",
            new MockCoordinate(47.4202611944959, 9.362182342510467));

    @Nested
    class Insert {
        @Test
        void insert() {
            KDTree<MockFacility> kdTree = new KDTree<>();
            // confirm that the tree is empty
            assertNull(kdTree.root);

            // insert a location, will be root
            kdTree.insert(berlin);
            assertEquals(berlin, kdTree.root.getLocation());
            assertNull(kdTree.root.getLeft());
            assertNull(kdTree.root.getRight());

            // insert a location with a smaller latitude coordinate (should go left)
            kdTree.insert(augsburg);
            assertEquals(berlin, kdTree.root.getLocation());
            assertEquals(augsburg, kdTree.root.getLeft().getLocation());
            assertNull(kdTree.root.getRight());
            assertNull(kdTree.root.getLeft().getLeft());
            assertNull(kdTree.root.getLeft().getRight());

            // insert a location with a smaller latitude than berlin and augsburg (but larger longitude than augsburg)
            // --> should go left and then right
            kdTree.insert(munich);
            assertEquals(berlin, kdTree.root.getLocation());
            assertEquals(augsburg, kdTree.root.getLeft().getLocation());
            assertEquals(munich, kdTree.root.getLeft().getRight().getLocation());
            assertNull(kdTree.root.getRight());
            assertNull(kdTree.root.getLeft().getLeft());
            assertNull(kdTree.root.getLeft().getRight().getLeft());
            assertNull(kdTree.root.getLeft().getRight().getRight());
            // insert a location where latitude is greater than root (should go right)
            kdTree.insert(vilnius);
            assertEquals(berlin, kdTree.root.getLocation());
            assertEquals(augsburg, kdTree.root.getLeft().getLocation());
            assertEquals(munich, kdTree.root.getLeft().getRight().getLocation());
            assertEquals(vilnius, kdTree.root.getRight().getLocation());
            assertNull(kdTree.root.getLeft().getLeft());
            assertNull(kdTree.root.getLeft().getRight().getLeft());
            assertNull(kdTree.root.getLeft().getRight().getRight());
            assertNull(kdTree.root.getRight().getLeft());
            assertNull(kdTree.root.getRight().getRight());
        }

        @Test
        void insert_withNull() {
            KDTree<MockFacility> kdTree = new KDTree<>();
            assertThrows(IllegalArgumentException.class, () -> kdTree.insert(null));
        }
    }

    @Nested
    class NearestNeighbour {
        @ParameterizedTest
        @MethodSource("provideTestCases")
        void nearestNeighbour(MockFacility location, MockFacility expectedNearestLocation) {
            KDTree<MockFacility> kdTree = buildTestKDTree();
            assertEquals(expectedNearestLocation, kdTree.nearestNeighbour(location));
        }

        @Test
        void nearestNeighbour_withNull() {
            KDTree<MockFacility> kdTree = buildTestKDTree();
            assertThrows(IllegalArgumentException.class, () -> kdTree.nearestNeighbour((MockFacility) null));
        }

        @Test
        void nearestNeighbour_withEmptyTree() {
            KDTree<MockFacility> kdTree = new KDTree<>();
            assertThrows(IllegalStateException.class, () -> kdTree.nearestNeighbour(munich));
        }

        private static Stream<Object[]> provideTestCases() {
            return Stream.of(new Object[]{parkingKreuzbleiche, sportsFacilityKreuzbleiche},
                    new Object[]{augsburg, munich});
        }
    }

    @Nested
    class RangeSearch {
        @Test
        void rangeSearch() {
            KDTree<MockFacility> kdTree = buildTestKDTree();

            // Note: because of x,y coordinate usage in the MockCoordinate class, the distance is calculated as
            // sqrt((x1-x2)^2 + (y1-y2)^2) --> and 1° latitude/longitude are treated as 1 km

            // includes nothing
            assertEquals(0, kdTree.rangeSearch(parkingKreuzbleiche, 0.0001).size());
            // includes sportsFacilityKreuzbleiche
            assertEquals(1, kdTree.rangeSearch(parkingKreuzbleiche, 0.001).size());
            // adds sportsFacilityKreuzbleiche and militarkantine
            assertEquals(2, kdTree.rangeSearch(parkingKreuzbleiche, 0.01).size());
            // adds Zürich
            assertEquals(3, kdTree.rangeSearch(parkingKreuzbleiche, 1).size());
        }

        @Test
        void rangeSearch_withCoordinateArgument() {
            KDTree<MockFacility> kdTree = buildTestKDTree();

            // includes all nodes in tree
            assertEquals(6, kdTree.rangeSearch(parkingKreuzbleiche.getCoordinate(), 7).size());
        }

        @Test
        void rangeSearch_withDoubleArguments() {
            KDTree<MockFacility> kdTree = buildTestKDTree();
            double x = parkingKreuzbleiche.getCoordinate().getFirstComponent();
            double y = parkingKreuzbleiche.getCoordinate().getSecondComponent();
            // includes all nodes in tree except Berlin (6.5 "km" away)
            assertEquals(5, kdTree.rangeSearch(x, y, 3).size());
        }

        @Test
        void rangeSearch_withNegativeDistance() {
            KDTree<MockFacility> kdTree = buildTestKDTree();
            assertThrows(IllegalArgumentException.class, () -> kdTree.rangeSearch(parkingKreuzbleiche, -1));
        }

        @Test
        void rangeSearch_withZeroDistance() {
            KDTree<MockFacility> kdTree = buildTestKDTree();
            assertThrows(IllegalArgumentException.class, () -> kdTree.rangeSearch(parkingKreuzbleiche, 0));
        }

        @Test
        void rangeSearch_withNull() {
            KDTree<MockFacility> kdTree = buildTestKDTree();
            assertThrows(IllegalArgumentException.class, () -> kdTree.rangeSearch((MockFacility) null, 500));
        }

        @Test
        void rangeSearch_withEmptyTree() {
            KDTree<MockFacility> kdTree = new KDTree<>();
            assertThrows(IllegalStateException.class, () -> kdTree.rangeSearch(munich, 500));
        }
    }

    private KDTree<MockFacility> buildTestKDTree() {
        // create some locations with near and far distances
        KDTree<MockFacility> kdTree = new KDTree<>();
        kdTree.insert(munich);
        kdTree.insert(berlin);
        kdTree.insert(frankfurt);
        kdTree.insert(zurich);
        kdTree.insert(militarkantine);
        kdTree.insert(sportsFacilityKreuzbleiche);
        return kdTree;
    }

}