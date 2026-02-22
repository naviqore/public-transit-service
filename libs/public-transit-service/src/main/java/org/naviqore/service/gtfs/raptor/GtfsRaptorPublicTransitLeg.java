package org.naviqore.service.gtfs.raptor;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.naviqore.service.LegType;
import org.naviqore.service.LegVisitor;
import org.naviqore.service.StopTime;
import org.naviqore.service.Trip;

@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = false)
public class GtfsRaptorPublicTransitLeg extends GtfsRaptorLeg implements org.naviqore.service.PublicTransitLeg {

    private final Trip trip;
    private final StopTime departure;
    private final StopTime arrival;

    GtfsRaptorPublicTransitLeg(int distance, int duration, Trip trip, StopTime departure, StopTime arrival) {
        super(LegType.PUBLIC_TRANSIT, distance, duration);
        this.trip = trip;
        this.departure = departure;
        this.arrival = arrival;
    }

    @Override
    public <T> T accept(LegVisitor<T> visitor) {
        return visitor.visit(this);
    }

}
