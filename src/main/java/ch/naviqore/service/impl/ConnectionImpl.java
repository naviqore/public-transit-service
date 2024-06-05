package ch.naviqore.service.impl;

import ch.naviqore.service.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
@ToString
public class ConnectionImpl implements Connection {

    private final List<Leg> legs;

    @Override
    public LocalDateTime getDepartureTime() {
        return legs.getFirst().accept(new LegVisitor<>() {
            @Override
            public LocalDateTime visit(PublicTransitLeg publicTransitLeg) {
                return publicTransitLeg.getDeparture().getDepartureTime();
            }

            @Override
            public LocalDateTime visit(Transfer transfer) {
                return transfer.getDepartureTime();
            }

            @Override
            public LocalDateTime visit(Walk walk) {
                return walk.getDepartureTime();
            }
        });
    }

    @Override
    public LocalDateTime getArrivalTime() {
        return legs.getLast().accept(new LegVisitor<>() {
            @Override
            public LocalDateTime visit(PublicTransitLeg publicTransitLeg) {
                return publicTransitLeg.getArrival().getArrivalTime();
            }

            @Override
            public LocalDateTime visit(Transfer transfer) {
                return transfer.getArrivalTime();
            }

            @Override
            public LocalDateTime visit(Walk walk) {
                return walk.getArrivalTime();
            }
        });
    }
}
