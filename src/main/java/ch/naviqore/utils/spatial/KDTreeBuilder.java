package ch.naviqore.utils.spatial;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class KDTreeBuilder<T extends Location<?>> {

    private ArrayList<T> locations = new ArrayList<>();

    public void addLocation(T location) {
        locations.add(location);
    }

    public void addLocations(Collection<T> locations) {
        for (T location : locations) {
            addLocation(location);
        }
    }

    public KDTree<T> build() {
        if (locations == null || locations.isEmpty()) {
            return new KDTree<>();
        }
        KDTree<T> tree = new KDTree<>();
        // remove null locations
        locations.removeIf(Objects::isNull);
        // sort locations to get a balanced tree
        locations = new ArrayList<>( balanceSortLocations(locations, 0) );
        locations.forEach(tree::insert);

        return tree;
    }

    List<T> balanceSortLocations(Collection<T> locations, int depth) {

        if (locations.size() <= 1) {
            return new ArrayList<T>(locations.stream().toList());
        }

        CoordinateComponentType axis = KDTreeUtils.getAxis(depth);

        // sort all locations by the axis
        List<T> sortedLocations = locations.stream().sorted((l1, l2) -> {
            double c1 = axis.getCoordinateComponent(l1.getCoordinate());
            double c2 = axis.getCoordinateComponent(l2.getCoordinate());
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
