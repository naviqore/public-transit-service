package ch.naviqore.service.impl;

import ch.naviqore.service.*;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@ToString(callSuper = true)
public class WalkImpl extends LegImpl implements Walk {

    private final WalkType walkType;
    private final LocalDateTime departureTime;
    private final LocalDateTime arrivalTime;
    private final Location sourceLocation;
    private final Location targetLocation;
    private final Stop stop;

    WalkImpl(int distance, int duration, WalkType walkType, LocalDateTime departureTime, LocalDateTime arrivalTime,
             Location sourceLocation, Location targetLocation, Stop stop) {
        super(LegType.WALK, distance, duration);
        this.walkType = walkType;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.sourceLocation = sourceLocation;
        this.targetLocation = targetLocation;
        this.stop = stop;
    }

    @Override
    public <T> T accept(LegVisitor<T> visitor) {
        return visitor.visit(this);
    }

}
