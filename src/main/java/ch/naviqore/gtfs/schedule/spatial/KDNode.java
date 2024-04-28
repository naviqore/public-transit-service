package ch.naviqore.gtfs.schedule.spatial;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class KDNode {
    private Coordinate location;
    private KDNode left;
    private KDNode right;

    public KDNode(Coordinate location) {
        this.location = location;
    }

}
