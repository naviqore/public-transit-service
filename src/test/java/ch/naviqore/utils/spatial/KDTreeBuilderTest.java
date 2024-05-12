package ch.naviqore.utils.spatial;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class KDTreeBuilderTest {
    @RequiredArgsConstructor
    static class TestCoordinate implements TwoDimensionalCoordinate {
        private final double x;
        private final double y;
        @Override
        public double getFirstComponent() {
            return x;
        }
        @Override
        public double getSecondComponent() {
            return y;
        }
        @Override
        public double distanceTo(TwoDimensionalCoordinate other) {
            return Math.sqrt(Math.pow(x - other.getFirstComponent(), 2) + Math.pow(y - other.getSecondComponent(), 2));
        }
    }

    @RequiredArgsConstructor
    static class TestLocation implements Location<TestCoordinate> {
        private final TestCoordinate coordinate;
        @Override
        public TestCoordinate getCoordinate() {
            return coordinate;
        }
    }

    private KDTreeBuilder<TestLocation> builder;

    @BeforeEach
    public void setUp() {
        builder = new KDTreeBuilder<>();
    }

    @Test
    public void test_build() {
        KDTree<TestLocation> tree = builder.build(getTestLocations());
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
    public void test_build_shouldReturnSingleNodeTree_givenListOfNullAndLocationValues() {
        List<TestLocation> locations = new ArrayList<>();
        locations.add(null);
        locations.add(new TestLocation(new TestCoordinate(5, 4)));
        locations.add(new TestLocation(new TestCoordinate(2, 7)));

        KDTree<TestLocation> tree = builder.build(locations);
        assertLocationMatchesExpectedCoordinate(5, 4, tree.root);
        assertLocationMatchesExpectedCoordinate(2, 7, tree.root.getLeft());
        assertNull(tree.root.getRight());
        assertNull(tree.root.getLeft().getLeft());
        assertNull(tree.root.getLeft().getRight());
    }

    @Test
    public void test_build_shouldReturnSingleNodeTree_givenSingleLocation() {
        ArrayList<TestLocation> locations = new ArrayList<>();
        locations.add(new TestLocation(new TestCoordinate(5, 4)));

        KDTree<TestLocation> tree = builder.build(locations);
        assertLocationMatchesExpectedCoordinate(5, 4, tree.root);
        assertNull(tree.root.getLeft());
        assertNull(tree.root.getRight());
    }

    @Test
    public void test_build_shouldReturnEmptyTree_whenLocationsIsNull() {
        KDTree<TestLocation> tree = builder.build(null);
        assertNull(tree.root);
    }

    @Test
    public void test_build_shouldReturnEmptyTree_whenLocationsIsEmpty() {
        KDTree<TestLocation> tree = builder.build(new ArrayList<>());
        assertNull(tree.root);
    }

    @Test
    public void test_balanceSortLocations() {
        List<TestLocation> locations = getTestLocations();
        List<TestLocation> balancedLocations = builder.balanceSortLocations(locations, 0);

        // make sure the locations are sorted correctly
        List<TestCoordinate> expectedSortOrter = new ArrayList<>();
        // first root
        expectedSortOrter.add(new TestCoordinate(5, 4));
        // left tree (all to the left and filling up rights going back up)
        expectedSortOrter.add(new TestCoordinate(2, 7));
        expectedSortOrter.add(new TestCoordinate(4, 5));
        expectedSortOrter.add(new TestCoordinate(3, 6));
        expectedSortOrter.add(new TestCoordinate(1, 8));
        // right tree (first lefts then rights)
        expectedSortOrter.add(new TestCoordinate(7, 2));
        expectedSortOrter.add(new TestCoordinate(8, 1));
        expectedSortOrter.add(new TestCoordinate(6, 3));

        for (int i = 0; i < locations.size(); i++) {
            var expectedCoordinate = expectedSortOrter.get(i);
            var balancedCoordinate = balancedLocations.get(i).getCoordinate();
            assertEquals(expectedCoordinate.getFirstComponent(), balancedCoordinate.getFirstComponent());
            assertEquals(expectedCoordinate.getSecondComponent(), balancedCoordinate.getSecondComponent());
        }
    }

    private void assertLocationMatchesExpectedCoordinate(double x, double y, KDNode<TestLocation> node) {
        assertEquals(x, node.getLocation().getCoordinate().getFirstComponent());
        assertEquals(y, node.getLocation().getCoordinate().getSecondComponent());
    }


    private ArrayList<TestLocation> getTestLocations() {
        ArrayList<TestLocation> locations = new ArrayList<>();
        locations.add(new TestLocation(new TestCoordinate(8, 1)));
        locations.add(new TestLocation(new TestCoordinate(5, 4)));
        locations.add(new TestLocation(new TestCoordinate(6, 3)));
        locations.add(new TestLocation(new TestCoordinate(1, 8)));
        locations.add(new TestLocation(new TestCoordinate(2, 7)));
        locations.add(new TestLocation(new TestCoordinate(7, 2)));
        locations.add(new TestLocation(new TestCoordinate(3, 6)));
        locations.add(new TestLocation(new TestCoordinate(4, 5)));
        return locations;
    }


}
