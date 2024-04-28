package ch.naviqore.raptor.model;

public record Stop(String id, int stopRouteIdx, int numberOfRoutes, int transferIdx, int numberOfTransfers) {
}
