package ch.naviqore.gtfs.schedule;

public record Route(String id, Agency agency, String shortName, String longName, RouteType type) {
}
