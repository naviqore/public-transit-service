package ch.naviqore.utils.spatial;

import ch.naviqore.gtfs.schedule.model.Coordinate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KDTreeTest {

    static StopFacilityMock munich = new StopFacilityMock("3", "Munich", new Coordinate(48.137154, 11.576124));
    static StopFacilityMock augsburg = new StopFacilityMock("2", "Augsburg", new Coordinate(48.370544, 10.89779));
    static StopFacilityMock berlin = new StopFacilityMock("4", "Berlin", new Coordinate(52.520008, 13.404954));
    static StopFacilityMock frankfurt = new StopFacilityMock("5", "Frankfurt", new Coordinate(50.110924, 8.682127));
    static StopFacilityMock zurich = new StopFacilityMock("6", "Zurich", new Coordinate(47.3769, 8.5417));
    static StopFacilityMock militarkantine = new StopFacilityMock("7", "Militarkantine", new Coordinate(47.42100820116168, 9.35977158264066));
    static StopFacilityMock sportsFacilityKreuzbleiche = new StopFacilityMock("8", "Sportanlage Kreuzbleiche", new Coordinate(47.41984757221546, 9.361976306041305));
    static StopFacilityMock parkingKreuzbleiche = new StopFacilityMock("9", "Pargarage Kreuzbleiche", new Coordinate(47.4202611944959, 9.362182342510467));

    private void insertCoordinatesIntoKDTree(KDTree<StopFacilityMock> kdTree) {
        // create some locations with near and far distances
        kdTree.insert(munich);
        kdTree.insert(berlin);
        kdTree.insert(frankfurt);
        kdTree.insert(zurich);
        kdTree.insert(militarkantine);
        kdTree.insert(sportsFacilityKreuzbleiche);
    }

    @ParameterizedTest
    @MethodSource("provideTestCases")
    void insertAndFindNearestNeighbour(StopFacilityMock location, StopFacilityMock expectedNearestLocation) {
        // Arrange
        KDTree<StopFacilityMock> kdTree = new KDTree<>();
        // Act
        insertCoordinatesIntoKDTree(kdTree);
        // Assert
        assertEquals(expectedNearestLocation, kdTree.nearestNeighbour(location));
    }

    private static Stream<Object[]> provideTestCases() {

        return Stream.of(
                new Object[]{parkingKreuzbleiche, sportsFacilityKreuzbleiche},
                new Object[]{augsburg, munich}
        );
    }
}