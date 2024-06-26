package ch.naviqore.utils.spatial.index;

import ch.naviqore.utils.spatial.Coordinate;
import ch.naviqore.utils.spatial.Location;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static ch.naviqore.utils.spatial.index.KDTree.getAxis;

@NoArgsConstructor(access = AccessLevel.PUBLIC)
@Slf4j
public class KDTreeBuilder<T extends Location<?>> {

    private ArrayList<T> locations = new ArrayList<>();

    public KDTreeBuilder<T> addLocation(T location) {
        if (location == null) {
            throw new IllegalArgumentException("Location must not be null");
        }
        locations.add(location);

        return this;
    }

    public KDTreeBuilder<T> addLocations(Collection<T> locations) {
        if (locations == null) {
            throw new IllegalArgumentException("locations must not be null");
        }
        for (T location : locations) {
            addLocation(location);
        }

        return this;
    }

    public KDTree<T> build() {
        if (locations == null || locations.isEmpty()) {
            throw new IllegalArgumentException("locations must not be null or empty");
        }
        log.info("Building spatial index for {} locations", locations.size());
        KDTree<T> tree = new KDTree<>();
        // sort locations to get a balanced tree
        locations = new ArrayList<>(balanceSortLocations(locations, 0));
        locations.forEach(tree::insert);

        return tree;
    }

    List<T> balanceSortLocations(Collection<T> locations, int depth) {
        if (locations.size() <= 1) {
            return new ArrayList<>(locations.stream().toList());
        }

        Coordinate.Axis axis = getAxis(depth);

        // sort all locations by the axis
        List<T> sortedLocations = locations.stream().sorted((l1, l2) -> {
            double c1 = l1.getCoordinate().getComponent(axis);
            double c2 = l2.getCoordinate().getComponent(axis);
            return Double.compare(c1, c2);
        }).toList();

        int medianIndex = sortedLocations.size() / 2;

        ArrayList<T> balancedList = new ArrayList<>();
        balancedList.add(sortedLocations.get(medianIndex));
        balancedList.addAll(balanceSortLocations(sortedLocations.subList(0, medianIndex), depth + 1));
        balancedList.addAll(
                balanceSortLocations(sortedLocations.subList(medianIndex + 1, sortedLocations.size()), depth + 1));

        return balancedList;
    }
}
