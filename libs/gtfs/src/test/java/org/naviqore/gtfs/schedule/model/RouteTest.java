package org.naviqore.gtfs.schedule.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.naviqore.gtfs.schedule.type.DefaultRouteType;
import org.naviqore.gtfs.schedule.type.HierarchicalVehicleType;
import org.naviqore.gtfs.schedule.type.RouteType;
import org.naviqore.timetable.type.TravelMode;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class RouteTest {

    private static final Agency AGENCY = new Agency("agency", "Agency", "https://agency.example.com",
            ZoneId.of("Europe/Zurich"));

    private static Route route(RouteType routeType) {
        return new Route("route", AGENCY, "R", "Route", routeType);
    }

    @ParameterizedTest
    @CsvSource({"BUS, BUS", "TROLLEYBUS, BUS", "TRAM, TRAM", "CABLE_TRAM, TRAM", "RAIL, RAIL", "MONORAIL, RAIL",
            "FERRY, SHIP", "SUBWAY, SUBWAY", "AERIAL_LIFT, AERIAL_LIFT", "FUNICULAR, FUNICULAR"})
    void shouldMapDefaultRouteTypeToTravelMode(DefaultRouteType routeType, TravelMode travelMode) {
        assertThat(route(routeType).getTravelMode()).isEqualTo(travelMode);
    }

    @Test
    void shouldMapHierarchicalVehicleTypeToTravelModeOfItsDefaultRouteType() {
        assertThat(route(HierarchicalVehicleType.SUBURBAN_RAILWAY).getTravelMode()).isEqualTo(TravelMode.RAIL);
    }

    @Test
    void shouldDescribeRouteTypeWithDescriptionOfRouteType() {
        assertThat(route(HierarchicalVehicleType.SUBURBAN_RAILWAY).getRouteTypeDescription()).isEqualTo(
                "Suburban Railway");
    }

}
