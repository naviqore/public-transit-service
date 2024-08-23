package ch.naviqore.app.controller;

import ch.naviqore.app.dto.TravelMode;
import ch.naviqore.service.PublicTransitService;
import ch.naviqore.utils.spatial.GeoCoordinate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;

@NoArgsConstructor(access = AccessLevel.NONE)
final class RoutingRequestValidator {

    public static GeoCoordinate validateCoordinate(double latitude, double longitude) {
        try {
            return new GeoCoordinate(latitude, longitude);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Coordinates must be valid, Latitude between -90 and 90 and Longitude between -180 and 180.");
        }
    }

    public static void validateQueryParams(int maxWalkingDuration, int maxTransferNumber, int maxTravelTime,
                                           int minTransferTime, boolean wheelchairAccessible, boolean bikeAllowed,
                                           EnumSet<TravelMode> travelModes, PublicTransitService service) {
        if (maxWalkingDuration < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Max walking duration must be greater than or equal to 0.");
        }
        if (maxTransferNumber < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Max transfer number must be greater than or equal to 0.");
        }
        if (maxTravelTime <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Max travel time must be greater than 0.");
        }
        if (minTransferTime < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Min transfer time must be greater than or equal to 0.");
        }

        // check support of routing features
        if (maxWalkingDuration != Integer.MAX_VALUE && !service.getRoutingFeatures().supportsMaxWalkingDuration()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Max Walking Duration is not supported by the router of this service.");
        }
        if (maxTransferNumber != Integer.MAX_VALUE && !service.getRoutingFeatures().supportsMaxNumTransfers()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Max Transfer Number is not supported by the router of this service.");
        }
        if (maxTravelTime != Integer.MAX_VALUE && !service.getRoutingFeatures().supportsMaxTravelTime()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Max Travel Time is not supported by the router of this service.");
        }
        if (minTransferTime != 0 && !service.getRoutingFeatures().supportsMinTransferDuration()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Min Transfer Duration is not supported by the router of this service.");
        }
        if (wheelchairAccessible && !service.getRoutingFeatures().supportsAccessibility()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Wheelchair Accessible routing is not supported by the router of this service.");
        }
        if (bikeAllowed && !service.getRoutingFeatures().supportsBikes()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Bike friendly routing is not supported by the router of this service.");
        }
        if (!travelModes.containsAll(EnumSet.allOf(TravelMode.class)) && !service.getRoutingFeatures()
                .supportsTravelModes()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Filtering travel modes is not supported by the router of this service.");
        }
    }

    public static void validateStopParameters(@Nullable String stopId, @Nullable Double latitude,
                                              @Nullable Double longitude, GlobalValidator.StopType stopType) {
        if (stopId == null) {
            if (latitude == null || longitude == null) {
                String stopTypeName = stopType.name().toLowerCase();
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Either " + stopTypeName + "StopId or " + stopTypeName + "Latitude and " + stopTypeName + "Longitude must be provided.");
            }
        } else {
            if (latitude != null || longitude != null) {
                String stopTypeName = stopType.name().toLowerCase();
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Only " + stopTypeName + "StopId or " + stopTypeName + "Latitude and " + stopTypeName + "Longitude must be provided, but not both.");
            }
        }
    }

    public static void validateStops(String sourceStopId, String targetStopId) {
        if (sourceStopId.equals(targetStopId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The source stop ID and target stop ID cannot be the same. Please provide different stop IDs for the source and target.");
        }
    }

    public static void validateCoordinates(GeoCoordinate sourceCoordinate, GeoCoordinate targetCoordinate) {
        if (sourceCoordinate.equals(targetCoordinate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The source and target coordinates cannot be the same. Please provide different coordinates for the source and target.");
        }
    }

}
