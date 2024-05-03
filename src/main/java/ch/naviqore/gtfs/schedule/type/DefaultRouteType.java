package ch.naviqore.gtfs.schedule.type;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public enum DefaultRouteType implements RouteType {
    TRAM(0, "Tram, Streetcar, Light rail. Any light rail or street level system within a metropolitan area."),
    SUBWAY(1, "Subway, Metro. Any underground rail system within a metropolitan area."),
    RAIL(2, "Rail. Used for intercity or long-distance travel."),
    BUS(3, "Bus. Used for short- and long-distance bus routes."),
    FERRY(4, "Ferry. Used for short- and long-distance boat service."),
    CABLE_TRAM(5,
            "Cable tram. Used for street-level rail cars where the cable runs beneath the vehicle (e.g., cable car in San Francisco)."),
    AERIAL_LIFT(6,
            "Aerial lift, suspended cable car (e.g., gondola lift, aerial tramway). Cable transport where cabins, cars, gondolas or open chairs are suspended by means of one or more cables."),
    FUNICULAR(7, "Funicular. Any rail system designed for steep inclines."),
    TROLLEYBUS(11, "Trolleybus. Electric buses that draw power from overhead wires using poles."),
    MONORAIL(12, "Monorail. Railway in which the track consists of a single rail or a beam.");

    private final int code;
    private final String description;

    public static DefaultRouteType parse(String code) {
        return parse(Integer.parseInt(code));
    }

    public static DefaultRouteType parse(int code) {
        for (DefaultRouteType type : DefaultRouteType.values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("No default route type with code " + code + " found");
    }
}
