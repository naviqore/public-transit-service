package ch.naviqore.app.controller;

import ch.naviqore.utils.spatial.GeoCoordinate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

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

    public static LocalDateTime setToNowIfNull(LocalDateTime dateTime) {
        return (dateTime == null) ? LocalDateTime.now() : dateTime;
    }

    public static void validateStopParameters(String stopId, double latitude, double longitude, String stopType) {
        if (stopId == null) {
            if (latitude == -91.0 || longitude == -181.0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Either " + stopType + "StopId or " + stopType + "Latitude and " + stopType + "Longitude must be provided.");
            }
        }
    }

}
