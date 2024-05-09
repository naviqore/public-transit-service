package ch.naviqore.gtfs.schedule.spatial;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class KDNode {
    private HasCoordinate location;
    private KDNode left;
    private KDNode right;

    public KDNode(HasCoordinate location) {
        this.location = location;
    }

}
