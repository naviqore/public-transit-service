package ch.naviqore.utils.spatial;

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
    static MockFacility parkingKreuzbleiche = new MockFacility("Pargarage Kreuzbleiche",
            new MockCoordinate(47.4202611944959, 9.362182342510467));

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

    private static Stream<Object[]> provideTestCases() {

        return Stream.of(new Object[]{parkingKreuzbleiche, sportsFacilityKreuzbleiche}, new Object[]{augsburg, munich});
    }
}