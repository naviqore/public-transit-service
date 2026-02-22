package org.naviqore.service.gtfs.raptor;

import lombok.*;
import org.naviqore.service.*;

import java.time.OffsetDateTime;
import java.util.List;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
@ToString
@EqualsAndHashCode
public class GtfsRaptorConnection implements Connection {

    private final List<Leg> legs;

    @Override
    public OffsetDateTime getDepartureTime() {
        return legs.getFirst().accept(new LegVisitor<>() {
            @Override
            public OffsetDateTime visit(PublicTransitLeg publicTransitLeg) {
                return publicTransitLeg.getDeparture().getDepartureTime();
            }

            @Override
            public OffsetDateTime visit(Transfer transfer) {
                return transfer.getDepartureTime();
            }

            @Override
            public OffsetDateTime visit(Walk walk) {
                return walk.getDepartureTime();
            }
        });
    }

    @Override
    public OffsetDateTime getArrivalTime() {
        return legs.getLast().accept(new LegVisitor<>() {
            @Override
            public OffsetDateTime visit(PublicTransitLeg publicTransitLeg) {
                return publicTransitLeg.getArrival().getArrivalTime();
            }

            @Override
            public OffsetDateTime visit(Transfer transfer) {
                return transfer.getArrivalTime();
            }

            @Override
            public OffsetDateTime visit(Walk walk) {
                return walk.getArrivalTime();
            }
        });
    }
}
