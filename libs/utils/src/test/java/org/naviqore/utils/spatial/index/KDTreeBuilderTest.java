package org.naviqore.utils.spatial.index;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.naviqore.utils.spatial.CartesianCoordinate;

import java.util.ArrayList;
import java.util.List;

public class KDTreeBuilderTest {

    private KDTreeBuilder<TestFacility> builder;

    @BeforeEach
    public void setUp() {
        builder = new KDTreeBuilder<>();
    }

    private void assertLocationMatchesExpectedCoordinate(double x, double y, KDTree.Node<TestFacility> node) {
        Assertions.assertEquals(x, node.getLocation().getCoordinate().getFirstComponent());
        Assertions.assertEquals(y, node.getLocation().getCoordinate().getSecondComponent());
    }

    private ArrayList<TestFacility> getTestLocations() {
        ArrayList<TestFacility> locations = new ArrayList<>();
        locations.add(new TestFacility("8,1", new CartesianCoordinate(8, 1)));
        locations.add(new TestFacility("5,4", new CartesianCoordinate(5, 4)));
        locations.add(new TestFacility("6,3", new CartesianCoordinate(6, 3)));
        locations.add(new TestFacility("1,8", new CartesianCoordinate(1, 8)));
        locations.add(new TestFacility("2,7", new CartesianCoordinate(2, 7)));
        locations.add(new TestFacility("7,2", new CartesianCoordinate(7, 2)));
        locations.add(new TestFacility("3,6", new CartesianCoordinate(3, 6)));
        locations.add(new TestFacility("4,5", new CartesianCoordinate(4, 5)));
        return locations;
    }

    @Nested
    class Build {
        @Test
        public void build() {
            builder.addLocations(getTestLocations());
            KDTree<TestFacility> tree = builder.build();
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
            builder.addLocation(new TestFacility("5,4", new CartesianCoordinate(5, 4)));
            KDTree<TestFacility> tree = builder.build();

            assertLocationMatchesExpectedCoordinate(5, 4, tree.root);
            Assertions.assertNull(tree.root.getLeft());
            Assertions.assertNull(tree.root.getRight());
        }

        @Test
        public void build_nullLocationAdded() {
            Assertions.assertThrows(IllegalArgumentException.class, () -> builder.addLocation(null));
        }

        @Test
        public void build_mixedNullAndLocationListAdded() {
            List<TestFacility> locations = new ArrayList<>();
            locations.add(null);
            locations.add(new TestFacility("5,4", new CartesianCoordinate(5, 4)));
            locations.add(new TestFacility("2,7", new CartesianCoordinate(2, 7)));

            Assertions.assertThrows(IllegalArgumentException.class, () -> builder.addLocations(locations));
        }

        @Test
        public void build_noLocationAdded() {
            Assertions.assertThrows(IllegalArgumentException.class, () -> builder.build());
        }
    }

    @Nested
    class BalanceSortLocations {
        @Test
        public void balanceSortLocations() {
            List<TestFacility> locations = getTestLocations();
            List<TestFacility> balancedLocations = builder.balanceSortLocations(locations, 0);

            // make sure the locations are sorted correctly
            List<CartesianCoordinate> expectedSortOrder = new ArrayList<>();
            // first root
            expectedSortOrder.add(new CartesianCoordinate(5, 4));
            // left tree (all to the left and filling up rights going back up)
            expectedSortOrder.add(new CartesianCoordinate(2, 7));
            expectedSortOrder.add(new CartesianCoordinate(4, 5));
            expectedSortOrder.add(new CartesianCoordinate(3, 6));
            expectedSortOrder.add(new CartesianCoordinate(1, 8));
            // right tree (first lefts then rights)
            expectedSortOrder.add(new CartesianCoordinate(7, 2));
            expectedSortOrder.add(new CartesianCoordinate(8, 1));
            expectedSortOrder.add(new CartesianCoordinate(6, 3));

            for (int i = 0; i < locations.size(); i++) {
                var expectedCoordinate = expectedSortOrder.get(i);
                var balancedCoordinate = balancedLocations.get(i).getCoordinate();
                Assertions.assertEquals(expectedCoordinate.getFirstComponent(), balancedCoordinate.getFirstComponent());
                Assertions.assertEquals(expectedCoordinate.getSecondComponent(),
                        balancedCoordinate.getSecondComponent());
            }
        }
    }

}
