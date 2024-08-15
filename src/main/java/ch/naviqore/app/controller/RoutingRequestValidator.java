package ch.naviqore.app.controller;

import ch.naviqore.utils.spatial.GeoCoordinate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
                                           int minTransferTime) {
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
    }

    public static void validateStopParameters(@Nullable String stopId, @Nullable Double latitude,
                                              @Nullable Double longitude, GlobalStopType stopType) {
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

}
