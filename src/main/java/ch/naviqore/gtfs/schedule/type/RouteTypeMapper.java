package ch.naviqore.gtfs.schedule.type;

public class RouteTypeMapper {

    public static DefaultRouteType map(RouteType routeType) {
        return switch (routeType) {
            case null -> null;
            case DefaultRouteType defaultRouteType -> defaultRouteType;
            case HierarchicalVehicleType hierarchicalVehicleType -> map(hierarchicalVehicleType);
            default -> throw new IllegalArgumentException("No mapping available for " + routeType);
        };
    }

    private static DefaultRouteType map(HierarchicalVehicleType routeType) {
        if (routeType == null) {
            return null;
        }
        return switch (routeType) {
            case RAILWAY_SERVICE, HIGH_SPEED_RAIL_SERVICE, LONG_DISTANCE_TRAINS, INTER_REGIONAL_RAIL_SERVICE,
                 CAR_TRANSPORT_RAIL_SERVICE, SLEEPER_RAIL_SERVICE, REGIONAL_RAIL_SERVICE, TOURIST_RAILWAY_SERVICE,
                 RAIL_SHUTTLE_WITHIN_COMPLEX, SUBURBAN_RAILWAY, REPLACEMENT_RAIL_SERVICE, SPECIAL_RAIL_SERVICE,
                 LORRY_TRANSPORT_RAIL_SERVICE, ALL_RAIL_SERVICES, CROSS_COUNTRY_RAIL_SERVICE,
                 VEHICLE_TRANSPORT_RAIL_SERVICE, ADDITIONAL_RAIL_SERVICE, RAIL_TAXI_SERVICE -> DefaultRouteType.RAIL;
            case RACK_AND_PINION_RAILWAY, FUNICULAR_SERVICE -> DefaultRouteType.FUNICULAR;
            case COACH_SERVICE, INTERNATIONAL_COACH_SERVICE, NATIONAL_COACH_SERVICE, SHUTTLE_COACH_SERVICE,
                 REGIONAL_COACH_SERVICE, SPECIAL_COACH_SERVICE, SIGHTSEEING_COACH_SERVICE, TOURIST_COACH_SERVICE,
                 COMMUTER_COACH_SERVICE, ALL_COACH_SERVICES -> DefaultRouteType.BUS;
            case URBAN_RAILWAY_SERVICE, METRO_SERVICE, ALL_URBAN_RAILWAY_SERVICES, MONORAIL -> DefaultRouteType.TRAM;
            case UNDERGROUND_SERVICE -> DefaultRouteType.SUBWAY;
            case BUS_SERVICE, REGIONAL_BUS_SERVICE, EXPRESS_BUS_SERVICE, LOCAL_BUS_SERVICE, NIGHT_BUS_SERVICE,
                 POST_BUS_SERVICE, SPECIAL_NEEDS_BUS, MOBILITY_BUS_SERVICE, MOBILITY_BUS_FOR_REGISTERED_DISABLED,
                 SIGHTSEEING_BUS, SHUTTLE_BUS, SCHOOL_BUS, SCHOOL_AND_PUBLIC_SERVICE_BUS, RAIL_REPLACEMENT_BUS_SERVICE,
                 DEMAND_AND_RESPONSE_BUS_SERVICE, ALL_BUS_SERVICES, TAXI_SERVICE, COMMUNAL_TAXI_SERVICE,
                 BIKE_TAXI_SERVICE, LICENSED_TAXI_SERVICE, PRIVATE_HIRE_SERVICE_VEHICLE, ALL_TAXI_SERVICES,
                 MISCELLANEOUS_SERVICE, HORSE_DRAWN_CARRIAGE -> DefaultRouteType.BUS;
            case TROLLEYBUS_SERVICE -> DefaultRouteType.TROLLEYBUS;
            case TRAM_SERVICE, CITY_TRAM_SERVICE, LOCAL_TRAM_SERVICE, REGIONAL_TRAM_SERVICE, SIGHTSEEING_TRAM_SERVICE,
                 SHUTTLE_TRAM_SERVICE, ALL_TRAM_SERVICES -> DefaultRouteType.TRAM;
            case WATER_TRANSPORT_SERVICE, FERRY_SERVICE, WATER_TAXI_SERVICE -> DefaultRouteType.FERRY;
            case AIR_SERVICE, AERIAL_LIFT_SERVICE, TELECABIN_SERVICE, CABLE_CAR_SERVICE, ELEVATOR_SERVICE,
                 CHAIR_LIFT_SERVICE, DRAG_LIFT_SERVICE, SMALL_TELECABIN_SERVICE, ALL_TELECABIN_SERVICES ->
                    DefaultRouteType.AERIAL_LIFT;
        };
    }
}