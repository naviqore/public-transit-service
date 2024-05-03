package ch.naviqore.gtfs.schedule.type;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public enum HierarchicalVehicleType implements RouteType {
    RAILWAY_SERVICE(100, "Railway Service", true),
    HIGH_SPEED_RAIL_SERVICE(101, "High Speed Rail Service", true),
    LONG_DISTANCE_TRAINS(102, "Long Distance Trains", true),
    INTER_REGIONAL_RAIL_SERVICE(103, "Inter Regional Rail Service", true),
    CAR_TRANSPORT_RAIL_SERVICE(104, "Car Transport Rail Service", false),
    SLEEPER_RAIL_SERVICE(105, "Sleeper Rail Service", true),
    REGIONAL_RAIL_SERVICE(106, "Regional Rail Service", true),
    TOURIST_RAILWAY_SERVICE(107, "Tourist Railway Service", true),
    RAIL_SHUTTLE_WITHIN_COMPLEX(108, "Rail Shuttle (Within Complex)", true),
    SUBURBAN_RAILWAY(109, "Suburban Railway", true),
    REPLACEMENT_RAIL_SERVICE(110, "Replacement Rail Service", false),
    SPECIAL_RAIL_SERVICE(111, "Special Rail Service", false),
    LORRY_TRANSPORT_RAIL_SERVICE(112, "Lorry Transport Rail Service", false),
    ALL_RAIL_SERVICES(113, "All Rail Services", false),
    CROSS_COUNTRY_RAIL_SERVICE(114, "Cross-Country Rail Service", false),
    VEHICLE_TRANSPORT_RAIL_SERVICE(115, "Vehicle Transport Rail Service", false),
    RACK_AND_PINION_RAILWAY(116, "Rack and Pinion Railway", false),
    ADDITIONAL_RAIL_SERVICE(117, "Additional Rail Service", false),

    COACH_SERVICE(200, "Coach Service", true),
    INTERNATIONAL_COACH_SERVICE(201, "International Coach Service", true),
    NATIONAL_COACH_SERVICE(202, "National Coach Service", true),
    SHUTTLE_COACH_SERVICE(203, "Shuttle Coach Service", false),
    REGIONAL_COACH_SERVICE(204, "Regional Coach Service", true),
    SPECIAL_COACH_SERVICE(205, "Special Coach Service", false),
    SIGHTSEEING_COACH_SERVICE(206, "Sightseeing Coach Service", false),
    TOURIST_COACH_SERVICE(207, "Tourist Coach Service", false),
    COMMUTER_COACH_SERVICE(208, "Commuter Coach Service", false),
    ALL_COACH_SERVICES(209, "All Coach Services", false),

    URBAN_RAILWAY_SERVICE(400, "Urban Railway Service", true),
    METRO_SERVICE(401, "Metro Service", true),
    UNDERGROUND_SERVICE(402, "Underground Service", true),
    ALL_URBAN_RAILWAY_SERVICES(404, "All Urban Railway Services", false),
    MONORAIL(405, "Monorail", true),

    BUS_SERVICE(700, "Bus Service", true),
    REGIONAL_BUS_SERVICE(701, "Regional Bus Service", true),
    EXPRESS_BUS_SERVICE(702, "Express Bus Service", true),
    LOCAL_BUS_SERVICE(704, "Local Bus Service", true),
    NIGHT_BUS_SERVICE(705, "Night Bus Service", false),
    POST_BUS_SERVICE(706, "Post Bus Service", false),
    SPECIAL_NEEDS_BUS(707, "Special Needs Bus", false),
    MOBILITY_BUS_SERVICE(708, "Mobility Bus Service", false),
    MOBILITY_BUS_FOR_REGISTERED_DISABLED(709, "Mobility Bus for Registered Disabled", false),
    SIGHTSEEING_BUS(710, "Sightseeing Bus", false),
    SHUTTLE_BUS(711, "Shuttle Bus", false),
    SCHOOL_BUS(712, "School Bus", false),
    SCHOOL_AND_PUBLIC_SERVICE_BUS(713, "School and Public Service Bus", false),
    RAIL_REPLACEMENT_BUS_SERVICE(714, "Rail Replacement Bus Service", false),
    DEMAND_AND_RESPONSE_BUS_SERVICE(715, "Demand and Response Bus Service", true),
    ALL_BUS_SERVICES(716, "All Bus Services", false),

    TROLLEYBUS_SERVICE(800, "Trolleybus Service", true),

    TRAM_SERVICE(900, "Tram Service", true),
    CITY_TRAM_SERVICE(901, "City Tram Service", false),
    LOCAL_TRAM_SERVICE(902, "Local Tram Service", false),
    REGIONAL_TRAM_SERVICE(903, "Regional Tram Service", false),
    SIGHTSEEING_TRAM_SERVICE(904, "Sightseeing Tram Service", false),
    SHUTTLE_TRAM_SERVICE(905, "Shuttle Tram Service", false),
    ALL_TRAM_SERVICES(906, "All Tram Services", false),

    WATER_TRANSPORT_SERVICE(1000, "Water Transport Service", true),
    AIR_SERVICE(1100, "Air Service", false),

    FERRY_SERVICE(1200, "Ferry Service", true),

    AERIAL_LIFT_SERVICE(1300, "Aerial Lift Service", true),
    TELECABIN_SERVICE(1301, "Telecabin Service", true),
    CABLE_CAR_SERVICE(1302, "Cable Car Service", false),
    ELEVATOR_SERVICE(1303, "Elevator Service", false),
    CHAIR_LIFT_SERVICE(1304, "Chair Lift Service", false),
    DRAG_LIFT_SERVICE(1305, "Drag Lift Service", false),
    SMALL_TELECABIN_SERVICE(1306, "Small Telecabin Service", false),
    ALL_TELECABIN_SERVICES(1307, "All Telecabin Services", false),

    FUNICULAR_SERVICE(1400, "Funicular Service", true),

    TAXI_SERVICE(1500, "Taxi Service", false),
    COMMUNAL_TAXI_SERVICE(1501, "Communal Taxi Service", true),
    WATER_TAXI_SERVICE(1502, "Water Taxi Service", false),
    RAIL_TAXI_SERVICE(1503, "Rail Taxi Service", false),
    BIKE_TAXI_SERVICE(1504, "Bike Taxi Service", false),
    LICENSED_TAXI_SERVICE(1505, "Licensed Taxi Service", false),
    PRIVATE_HIRE_SERVICE_VEHICLE(1506, "Private Hire Service Vehicle", false),
    ALL_TAXI_SERVICES(1507, "All Taxi Services", false),

    MISCELLANEOUS_SERVICE(1700, "Miscellaneous Service", true),
    HORSE_DRAWN_CARRIAGE(1702, "Horse-drawn Carriage", true);

    private final int code;
    private final String description;
    private final boolean supported;

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
