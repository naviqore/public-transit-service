package ch.naviqore.service.gtfs.raptor;

import ch.naviqore.service.LegType;
import ch.naviqore.service.LegVisitor;
import ch.naviqore.service.Stop;
import ch.naviqore.service.Transfer;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@ToString(callSuper = true)
public class GtfsRaptorTransfer extends GtfsRaptorLeg implements Transfer {

    private final LocalDateTime departureTime;
    private final LocalDateTime arrivalTime;
    private final Stop sourceStop;
    private final Stop targetStop;

    GtfsRaptorTransfer(int distance, int duration, LocalDateTime departureTime, LocalDateTime arrivalTime,
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