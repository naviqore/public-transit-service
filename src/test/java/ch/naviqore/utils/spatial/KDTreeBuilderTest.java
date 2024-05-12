package ch.naviqore.utils.spatial;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class KDTreeBuilderTest {
    private KDTreeBuilder<MockFacility> builder;

    @BeforeEach
    public void setUp() {
        builder = new KDTreeBuilder<>();
    }

    @Test
    public void test_build() {
        builder.addLocations(getTestLocations());
        KDTree<MockFacility> tree = builder.build();
        // tree should look like
        //            ---(5, 4)---
        //      (2, 7)             (7,2)
        //    (4, 5)  (1, 8)   (8, 1)  (6, 3)
        // (3, 6)
        assertLocationMatchesExpectedCoordinate(5, 4, tree.root);
        assertLocationMatchesExpectedCoordinate(2, 7, tree.root.getLeft());
        assertLocationMatchesExpectedCoordinate(7, 2, tree.root.getRight());
        assertLocationMatchesExpectedCoordinate(4, 5, tree.root.getLeft().getLeft());
        assertLocationMatchesExpectedCoordinate(3, 6, tree.root.getLeft().getLeft().getLeft());
        assertLocationMatchesExpectedCoordinate(1, 8, tree.root.getLeft().getRight());
        assertLocationMatchesExpectedCoordinate(8, 1, tree.root.getRight().getLeft());
        assertLocationMatchesExpectedCoordinate(6, 3, tree.root.getRight().getRight());
    }

    @Test
    public void test_build_shouldReturnSingleNodeTree_givenSingleLocation() {
        builder.addLocation(new MockFacility("5,4", new MockCoordinate(5, 4)));
        KDTree<MockFacility> tree = builder.build();

        assertLocationMatchesExpectedCoordinate(5, 4, tree.root);
        assertNull(tree.root.getLeft());
        assertNull(tree.root.getRight());
    }

    @Test
    public void test_build_shouldRaiseException_whenNullLocationIsAdded() {
        assertThrows(IllegalArgumentException.class, () -> builder.addLocation(null));
    }

    @Test
    public void test_build_shouldRaiseException_whenListOfNullAndLocationValuesIsAdded() {
        List<MockFacility> locations = new ArrayList<>();
        locations.add(null);
        locations.add(new MockFacility("5,4", new MockCoordinate(5, 4)));
        locations.add(new MockFacility("2,7", new MockCoordinate(2, 7)));

        assertThrows(IllegalArgumentException.class, () -> builder.addLocations(locations));
    }

    @Test
    public void test_build_shouldRaiseException_whenLocationsIsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> builder.build());
    }

    @Test
    public void test_balanceSortLocations() {
        List<MockFacility> locations = getTestLocations();
        List<MockFacility> balancedLocations = builder.balanceSortLocations(locations, 0);

        // make sure the locations are sorted correctly
        List<MockCoordinate> expectedSortOrter = new ArrayList<>();
        // first root
        expectedSortOrter.add(new MockCoordinate(5, 4));
        // left tree (all to the left and filling up rights going back up)
        expectedSortOrter.add(new MockCoordinate(2, 7));
        expectedSortOrter.add(new MockCoordinate(4, 5));
        expectedSortOrter.add(new MockCoordinate(3, 6));
        expectedSortOrter.add(new MockCoordinate(1, 8));
        // right tree (first lefts then rights)
        expectedSortOrter.add(new MockCoordinate(7, 2));
        expectedSortOrter.add(new MockCoordinate(8, 1));
        expectedSortOrter.add(new MockCoordinate(6, 3));

        for (int i = 0; i < locations.size(); i++) {
            var expectedCoordinate = expectedSortOrter.get(i);
            var balancedCoordinate = balancedLocations.get(i).getCoordinate();
            assertEquals(expectedCoordinate.getFirstComponent(), balancedCoordinate.getFirstComponent());
            assertEquals(expectedCoordinate.getSecondComponent(), balancedCoordinate.getSecondComponent());
        }
    }

    private void assertLocationMatchesExpectedCoordinate(double x, double y, KDNode<MockFacility> node) {
        assertEquals(x, node.getLocation().getCoordinate().getFirstComponent());
        assertEquals(y, node.getLocation().getCoordinate().getSecondComponent());
    }


    private ArrayList<MockFacility> getTestLocations() {
        ArrayList<MockFacility> locations = new ArrayList<>();
        locations.add(new MockFacility("8,1", new MockCoordinate(8, 1)));
        locations.add(new MockFacility("5,4", new MockCoordinate(5, 4)));
        locations.add(new MockFacility("6,3", new MockCoordinate(6, 3)));
        locations.add(new MockFacility("1,8", new MockCoordinate(1, 8)));
        locations.add(new MockFacility("2,7", new MockCoordinate(2, 7)));
        locations.add(new MockFacility("7,2", new MockCoordinate(7, 2)));
        locations.add(new MockFacility("3,6", new MockCoordinate(3, 6)));
        locations.add(new MockFacility("4,5", new MockCoordinate(4, 5)));
        return locations;
    }


}
