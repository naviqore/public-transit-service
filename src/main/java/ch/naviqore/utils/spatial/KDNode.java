package ch.naviqore.utils.spatial;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KDNode <T> {
    private T location;
    private KDNode<T> left;
    private KDNode<T> right;

    public KDNode(T location) {
        this.location = location;
    }

}
