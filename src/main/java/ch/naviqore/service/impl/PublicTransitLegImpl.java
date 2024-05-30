package ch.naviqore.service.impl;

import ch.naviqore.service.*;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class PublicTransitLegImpl extends LegImpl implements PublicTransitLeg {

    private final Trip trip;
    private final StopTime arrival;
    private final StopTime departure;

    PublicTransitLegImpl(int distance, int duration, Trip trip, StopTime arrival, StopTime departure) {
        super(LegType.PUBLIC_TRANSIT, distance, duration);
        this.trip = trip;
        this.arrival = arrival;
        this.departure = departure;
    }

    @Override
    public <T> T accept(LegVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
