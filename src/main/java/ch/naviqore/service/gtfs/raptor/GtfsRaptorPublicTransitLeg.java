package ch.naviqore.service.gtfs.raptor;

import ch.naviqore.service.LegType;
import ch.naviqore.service.LegVisitor;
import ch.naviqore.service.StopTime;
import ch.naviqore.service.Trip;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class GtfsRaptorPublicTransitLeg extends GtfsRaptorLeg implements ch.naviqore.service.PublicTransitLeg {

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
