package ch.naviqore.utils.spatial;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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

    @Nested
    class Build {
        @Test
        public void build() {
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
        public void build_withSingleLocation() {
            builder.addLocation(new MockFacility("5,4", new MockCoordinate(5, 4)));
            KDTree<MockFacility> tree = builder.build();

            assertLocationMatchesExpectedCoordinate(5, 4, tree.root);
            assertNull(tree.root.getLeft());
            assertNull(tree.root.getRight());
        }

        @Test
        public void build_nullLocationAdded() {
            assertThrows(IllegalArgumentException.class, () -> builder.addLocation(null));
        }

        @Test
        public void build_mixedNullAndLocationListAdded() {
            List<MockFacility> locations = new ArrayList<>();
            locations.add(null);
            locations.add(new MockFacility("5,4", new MockCoordinate(5, 4)));
            locations.add(new MockFacility("2,7", new MockCoordinate(2, 7)));

            assertThrows(IllegalArgumentException.class, () -> builder.addLocations(locations));
        }

        @Test
        public void build_noLocationAdded() {
            assertThrows(IllegalArgumentException.class, () -> builder.build());
        }
    }

    @Nested
    class BalanceSortLocations {
        @Test
        public void balanceSortLocations() {
            List<MockFacility> locations = getTestLocations();
            List<MockFacility> balancedLocations = builder.balanceSortLocations(locations, 0);

            // make sure the locations are sorted correctly
            List<MockCoordinate> expectedSortOrder = new ArrayList<>();
            // first root
            expectedSortOrder.add(new MockCoordinate(5, 4));
            // left tree (all to the left and filling up rights going back up)
            expectedSortOrder.add(new MockCoordinate(2, 7));
            expectedSortOrder.add(new MockCoordinate(4, 5));
            expectedSortOrder.add(new MockCoordinate(3, 6));
            expectedSortOrder.add(new MockCoordinate(1, 8));
            // right tree (first lefts then rights)
            expectedSortOrder.add(new MockCoordinate(7, 2));
            expectedSortOrder.add(new MockCoordinate(8, 1));
            expectedSortOrder.add(new MockCoordinate(6, 3));

            for (int i = 0; i < locations.size(); i++) {
                var expectedCoordinate = expectedSortOrder.get(i);
                var balancedCoordinate = balancedLocations.get(i).getCoordinate();
                assertEquals(expectedCoordinate.getFirstComponent(), balancedCoordinate.getFirstComponent());
                assertEquals(expectedCoordinate.getSecondComponent(), balancedCoordinate.getSecondComponent());
            }
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
