package org.naviqore.service.gtfs.raptor;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.naviqore.service.LegType;
import org.naviqore.service.LegVisitor;
import org.naviqore.service.Stop;
import org.naviqore.service.Transfer;

import java.time.OffsetDateTime;

@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = false)
public class GtfsRaptorTransfer extends GtfsRaptorLeg implements Transfer {

    private final OffsetDateTime departureTime;
    private final OffsetDateTime arrivalTime;
    private final Stop sourceStop;
    private final Stop targetStop;

    GtfsRaptorTransfer(int distance, int duration, OffsetDateTime departureTime, OffsetDateTime arrivalTime,
                       Stop sourceStop, Stop targetStop) {
        super(LegType.WALK, distance, duration);
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.sourceStop = sourceStop;
        this.targetStop = targetStop;
    }

    @Override
    public <T> T accept(LegVisitor<T> visitor) {
        return visitor.visit(this);
    }

}