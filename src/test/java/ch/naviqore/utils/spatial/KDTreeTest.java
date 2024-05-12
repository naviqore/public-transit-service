package ch.naviqore.utils.spatial;

import ch.naviqore.gtfs.schedule.model.Coordinate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class KDTreeTest {
    static StopFacilityMock vilnius = new StopFacilityMock("1", "Vilnius", new Coordinate(54.68916, 25.2798));
    static StopFacilityMock munich = new StopFacilityMock("3", "Munich", new Coordinate(48.137154, 11.576124));
    static StopFacilityMock augsburg = new StopFacilityMock("2", "Augsburg", new Coordinate(48.370544, 10.89779));
    static StopFacilityMock berlin = new StopFacilityMock("4", "Berlin", new Coordinate(52.520008, 13.404954));
    static StopFacilityMock frankfurt = new StopFacilityMock("5", "Frankfurt", new Coordinate(50.110924, 8.682127));
    static StopFacilityMock zurich = new StopFacilityMock("6", "Zurich", new Coordinate(47.3769, 8.5417));
    static StopFacilityMock militarkantine = new StopFacilityMock("7", "Militarkantine", new Coordinate(47.42100820116168, 9.35977158264066));
    static StopFacilityMock sportsFacilityKreuzbleiche = new StopFacilityMock("8", "Sportanlage Kreuzbleiche", new Coordinate(47.41984757221546, 9.361976306041305));
    static StopFacilityMock parkingKreuzbleiche = new StopFacilityMock("9", "Pargarage Kreuzbleiche", new Coordinate(47.4202611944959, 9.362182342510467));
    @Test
    void insert() {
        KDTree<StopFacilityMock> kdTree = new KDTree<>();
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
        KDTree<StopFacilityMock> kdTree = new KDTree<>();
        assertThrows(IllegalArgumentException.class, () -> kdTree.insert(null));
    }

    @ParameterizedTest
    @MethodSource("provideTestCases")
    void nearestNeighbour(StopFacilityMock location, StopFacilityMock expectedNearestLocation) {
        KDTree<StopFacilityMock> kdTree = buildTestKDTree();
        assertEquals(expectedNearestLocation, kdTree.nearestNeighbour(location));
    }

    @Test
    void nearestNeighbour_withNull() {
        KDTree<StopFacilityMock> kdTree = buildTestKDTree();
        assertThrows(IllegalArgumentException.class, () -> kdTree.nearestNeighbour(null));
    }

    @Test
    void nearestNeighbour_withEmptyTree() {
        KDTree<StopFacilityMock> kdTree = new KDTree<>();
        assertThrows(IllegalStateException.class, () -> kdTree.nearestNeighbour(munich));
    }

    private KDTree<StopFacilityMock> buildTestKDTree() {
        // create some locations with near and far distances
        KDTree<StopFacilityMock> kdTree = new KDTree<>();
        kdTree.insert(munich);
        kdTree.insert(berlin);
        kdTree.insert(frankfurt);
        kdTree.insert(zurich);
        kdTree.insert(militarkantine);
        kdTree.insert(sportsFacilityKreuzbleiche);
        return kdTree;
    }

    private static Stream<Object[]> provideTestCases() {

        return Stream.of(
                new Object[]{parkingKreuzbleiche, sportsFacilityKreuzbleiche},
                new Object[]{augsburg, munich}
        );
    }
}