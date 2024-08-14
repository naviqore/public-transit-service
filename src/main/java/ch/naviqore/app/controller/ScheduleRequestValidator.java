package ch.naviqore.app.controller;

import ch.naviqore.service.ScheduleInformationService;
import ch.naviqore.service.exception.StopNotFoundException;
import ch.naviqore.utils.spatial.GeoCoordinate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
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

    public static ch.naviqore.service.Stop validateAndGet(String stopId, ScheduleInformationService service) {
        try {
            return service.getStopById(stopId);
        } catch (StopNotFoundException e) {
            String errorMessage = String.format("The requested stop with ID '%s' was not found.", stopId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, errorMessage, e);
        }
    }

    public static LocalDateTime validateAndSetDefaultDateTime(LocalDateTime dateTime) {
        return (dateTime == null) ? LocalDateTime.now() : dateTime;
    }

    public static void validateUntilDateTime(LocalDateTime departureDateTime, LocalDateTime untilDateTime) {
        if (untilDateTime != null && untilDateTime.isBefore(departureDateTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Until date time must be after departure date time");
        }
    }
}
