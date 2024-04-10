package ch.naviqore.raptor.model;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RouteTraversal {
    private final StopTime[] stopTimes;
    private final Route[] routes;
    private final Stop[] routeStops;
}
