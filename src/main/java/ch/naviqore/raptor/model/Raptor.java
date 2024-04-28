package ch.naviqore.raptor.model;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Log4j2
public class Raptor {
    private final RouteTraversal routeTraversal;

    public static RaptorBuilder builder() {
        return new RaptorBuilder();
    }

    public void routeEarliestArrival(String sourceStop, String targetStop, int departureTime) {
        log.debug("Routing earliest arrival from {} to {} at {}", sourceStop, targetStop, departureTime);
    }
}
