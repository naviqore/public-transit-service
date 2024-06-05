package ch.naviqore.service.impl;

import ch.naviqore.service.*;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@ToString(callSuper = true)
public class PublicTransitLegImpl extends LegImpl implements PublicTransitLeg {

    private final Trip trip;
    private final StopTime departure;
    private final StopTime arrival;

    PublicTransitLegImpl(int distance, int duration, Trip trip, StopTime departure, StopTime arrival) {
        super(LegType.PUBLIC_TRANSIT, distance, duration);
        this.trip = trip;
        this.departure = departure;
        this.arrival = arrival;
    }

    @Override
    public <T> T accept(LegVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public LocalDateTime getDepartureTime() {
        return departure.getDepartureTime();
    }

    @Override
    public LocalDateTime getArrivalTime() {
        return arrival.getArrivalTime();
    }

}
