package ch.naviqore.gtfs.schedule.type;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public enum HierarchicalVehicleType implements RouteType {
    RAILWAY_SERVICE(100, "Railway Service"),
    HIGH_SPEED_RAIL_SERVICE(101, "High Speed Rail Service"),
    LONG_DISTANCE_TRAINS(102, "Long Distance Trains"),
    INTER_REGIONAL_RAIL_SERVICE(103, "Inter Regional Rail Service"),
    CAR_TRANSPORT_RAIL_SERVICE(104, "Car Transport Rail Service"),
    SLEEPER_RAIL_SERVICE(105, "Sleeper Rail Service"),
    REGIONAL_RAIL_SERVICE(106, "Regional Rail Service"),
    TOURIST_RAILWAY_SERVICE(107, "Tourist Railway Service"),
    RAIL_SHUTTLE_WITHIN_COMPLEX(108, "Rail Shuttle (Within Complex)"),
    SUBURBAN_RAILWAY(109, "Suburban Railway"),
    REPLACEMENT_RAIL_SERVICE(110, "Replacement Rail Service"),
    SPECIAL_RAIL_SERVICE(111, "Special Rail Service"),
    LORRY_TRANSPORT_RAIL_SERVICE(112, "Lorry Transport Rail Service"),
    ALL_RAIL_SERVICES(113, "All Rail Services"),
    CROSS_COUNTRY_RAIL_SERVICE(114, "Cross-Country Rail Service"),
    VEHICLE_TRANSPORT_RAIL_SERVICE(115, "Vehicle Transport Rail Service"),
    RACK_AND_PINION_RAILWAY(116, "Rack and Pinion Railway"),
    ADDITIONAL_RAIL_SERVICE(117, "Additional Rail Service"),

    COACH_SERVICE(200, "Coach Service"),
    INTERNATIONAL_COACH_SERVICE(201, "International Coach Service"),
    NATIONAL_COACH_SERVICE(202, "National Coach Service"),
    SHUTTLE_COACH_SERVICE(203, "Shuttle Coach Service"),
    REGIONAL_COACH_SERVICE(204, "Regional Coach Service"),
    SPECIAL_COACH_SERVICE(205, "Special Coach Service"),
    SIGHTSEEING_COACH_SERVICE(206, "Sightseeing Coach Service"),
    TOURIST_COACH_SERVICE(207, "Tourist Coach Service"),
    COMMUTER_COACH_SERVICE(208, "Commuter Coach Service"),
    ALL_COACH_SERVICES(209, "All Coach Services"),

    URBAN_RAILWAY_SERVICE(400, "Urban Railway Service"),
    METRO_SERVICE(401, "Metro Service"),
    UNDERGROUND_SERVICE(402, "Underground Service"),
    ALL_URBAN_RAILWAY_SERVICES(404, "All Urban Railway Services"),
    MONORAIL(405, "Monorail"),

    BUS_SERVICE(700, "Bus Service"),
    REGIONAL_BUS_SERVICE(701, "Regional Bus Service"),
    EXPRESS_BUS_SERVICE(702, "Express Bus Service"),
    LOCAL_BUS_SERVICE(704, "Local Bus Service"),
    NIGHT_BUS_SERVICE(705, "Night Bus Service"),
    POST_BUS_SERVICE(706, "Post Bus Service"),
    SPECIAL_NEEDS_BUS(707, "Special Needs Bus"),
    MOBILITY_BUS_SERVICE(708, "Mobility Bus Service"),
    MOBILITY_BUS_FOR_REGISTERED_DISABLED(709, "Mobility Bus for Registered Disabled"),
    SIGHTSEEING_BUS(710, "Sightseeing Bus"),
    SHUTTLE_BUS(711, "Shuttle Bus"),
    SCHOOL_BUS(712, "School Bus"),
    SCHOOL_AND_PUBLIC_SERVICE_BUS(713, "School and Public Service Bus"),
    RAIL_REPLACEMENT_BUS_SERVICE(714, "Rail Replacement Bus Service"),
    DEMAND_AND_RESPONSE_BUS_SERVICE(715, "Demand and Response Bus Service"),
    ALL_BUS_SERVICES(716, "All Bus Services"),

    TROLLEYBUS_SERVICE(800, "Trolleybus Service"),

    TRAM_SERVICE(900, "Tram Service"),
    CITY_TRAM_SERVICE(901, "City Tram Service"),
    LOCAL_TRAM_SERVICE(902, "Local Tram Service"),
    REGIONAL_TRAM_SERVICE(903, "Regional Tram Service"),
    SIGHTSEEING_TRAM_SERVICE(904, "Sightseeing Tram Service"),
    SHUTTLE_TRAM_SERVICE(905, "Shuttle Tram Service"),
    ALL_TRAM_SERVICES(906, "All Tram Services"),

    WATER_TRANSPORT_SERVICE(1000, "Water Transport Service"),
    AIR_SERVICE(1100, "Air Service"),

    FERRY_SERVICE(1200, "Ferry Service"),

    AERIAL_LIFT_SERVICE(1300, "Aerial Lift Service"),
    TELECABIN_SERVICE(1301, "Telecabin Service"),
    CABLE_CAR_SERVICE(1302, "Cable Car Service"),
    ELEVATOR_SERVICE(1303, "Elevator Service"),
    CHAIR_LIFT_SERVICE(1304, "Chair Lift Service"),
    DRAG_LIFT_SERVICE(1305, "Drag Lift Service"),
    SMALL_TELECABIN_SERVICE(1306, "Small Telecabin Service"),
    ALL_TELECABIN_SERVICES(1307, "All Telecabin Services"),

    FUNICULAR_SERVICE(1400, "Funicular Service"),

    TAXI_SERVICE(1500, "Taxi Service"),
    COMMUNAL_TAXI_SERVICE(1501, "Communal Taxi Service"),
    WATER_TAXI_SERVICE(1502, "Water Taxi Service"),
    RAIL_TAXI_SERVICE(1503, "Rail Taxi Service"),
    BIKE_TAXI_SERVICE(1504, "Bike Taxi Service"),
    LICENSED_TAXI_SERVICE(1505, "Licensed Taxi Service"),
    PRIVATE_HIRE_SERVICE_VEHICLE(1506, "Private Hire Service Vehicle"),
    ALL_TAXI_SERVICES(1507, "All Taxi Services"),

    MISCELLANEOUS_SERVICE(1700, "Miscellaneous Service"),
    HORSE_DRAWN_CARRIAGE(1702, "Horse-drawn Carriage");

    private final int code;
    private final String description;

    public static HierarchicalVehicleType parse(String code) {
        return parse(Integer.parseInt(code));
    }

    public static HierarchicalVehicleType parse(int code) {
        for (HierarchicalVehicleType type : HierarchicalVehicleType.values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("No hierarchical vehicle type with code " + code + " found");
    }
}
