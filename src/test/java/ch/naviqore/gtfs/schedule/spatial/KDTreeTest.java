package ch.naviqore.gtfs.schedule.spatial;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KDTreeTest {
    private final Coordinate munich = new Coordinate(48.137154, 11.576124);
    private final Coordinate berlin = new Coordinate(52.520008, 13.404954);
    private final Coordinate frankfurt = new Coordinate(50.110924, 8.682127);
    private final Coordinate zurich = new Coordinate(47.3769, 8.5417);
    private final Coordinate stGallenMilitarkantine = new Coordinate(47.42100820116168, 9.35977158264066);
    private final Coordinate stGallenSportanlageKreuzbleiche = new Coordinate(47.41984757221546, 9.361976306041305);

    @Test
    void insertAndFindNearestNeighbour() {
        // Arrange
        KDTree<Coordinate> kdTree = createKDTree();
        // Act
        insertCoordinatesIntoKDTree(kdTree);
        // Assert
        testNearestNeighbourWithinKreuzbleiche(kdTree);
        testNearestNeighbourToMunich(kdTree);
    }

    private KDTree<Coordinate> createKDTree() {
        return new KDTree<>();
    }

    private void insertCoordinatesIntoKDTree(KDTree<Coordinate> kdTree) {
        kdTree.insert(munich);
        kdTree.insert(berlin);
        kdTree.insert(frankfurt);
        kdTree.insert(zurich);
        kdTree.insert(stGallenMilitarkantine);
        kdTree.insert(stGallenSportanlageKreuzbleiche);
    }

    private void testNearestNeighbourWithinKreuzbleiche(KDTree<Coordinate> kdTree) {
        final Coordinate stGallenParkgarageKreuzbleiche = new Coordinate(47.4202611944959, 9.362182342510467);
        assertEquals(stGallenSportanlageKreuzbleiche, kdTree.nearestNeighbour(stGallenParkgarageKreuzbleiche));
    }

    private  void testNearestNeighbourToMunich(KDTree<Coordinate> kdTree) {
        final Coordinate augsburg = new Coordinate(48.371827522076615, 10.891993996832017);
        assertEquals(munich, kdTree.nearestNeighbour(augsburg));
    }
}