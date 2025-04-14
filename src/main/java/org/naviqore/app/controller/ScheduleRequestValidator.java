package org.naviqore.app.controller;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.naviqore.utils.spatial.GeoCoordinate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@NoArgsConstructor(access = AccessLevel.NONE)
final class ScheduleRequestValidator {

    public static void validateLimit(int limit) {
        if (limit <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit must be greater than 0");
        }
    }

    public static void validateMaxDistance(int maxDistance) {
        if (maxDistance < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Max distance cannot be negative");
        }
    }

    public static GeoCoordinate validateGeoCoordinate(double latitude, double longitude) {
        try {
            return new GeoCoordinate(latitude, longitude);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    public static void validateUntilDateTime(LocalDateTime departureDateTime, LocalDateTime untilDateTime) {
        if (untilDateTime != null && untilDateTime.isBefore(departureDateTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Until date time must be after departure date time");
        }
    }

}
