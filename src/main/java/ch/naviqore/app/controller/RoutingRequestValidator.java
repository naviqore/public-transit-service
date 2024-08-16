package ch.naviqore.app.controller;

import ch.naviqore.service.PublicTransitService;
import ch.naviqore.service.TravelMode;
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

        // If the service does not support accessibility information, bike information, or travel mode information,
        // only default values are allowed (i.e., false for wheelchairAccessible, false for bikeAllowed,
        // and all travel modes).
        if (wheelchairAccessible && !service.hasAccessibilityInformation()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Accessibility information is not available for this service.");
        }
        if (bikeAllowed && !service.hasBikeInformation()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Bike information is not available for this service.");
        }
        if (!travelModes.containsAll(EnumSet.allOf(TravelMode.class)) && !service.hasTravelModeInformation()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Service does not support travel mode information.");
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

}
