package org.naviqore.app.service;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.naviqore.app.dto.TravelMode;
import org.naviqore.app.exception.InvalidCoordinatesException;
import org.naviqore.app.exception.InvalidDateTimeException;
import org.naviqore.app.exception.InvalidRoutingParametersException;
import org.naviqore.app.exception.UnsupportedRoutingFeatureException;
import org.naviqore.service.RoutingFeatures;
import org.naviqore.service.ScheduleInformationService;
import org.naviqore.utils.spatial.GeoCoordinate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;

/**
 * Service-layer validator for business rules and domain constraints.
 * <p>
 * Validates business logic that goes beyond simple parameter validation, including service feature support, schedule
 * validity, and domain constraints.
 */
@NoArgsConstructor(access = AccessLevel.NONE)
public final class ValidationService {

    /**
     * Validates coordinates and creates a GeoCoordinate object.
     *
     * @throws InvalidCoordinatesException if coordinates are invalid
     */
    public static GeoCoordinate validateAndCreateCoordinate(double latitude, double longitude) {
        try {
            return new GeoCoordinate(latitude, longitude);
        } catch (IllegalArgumentException e) {
            throw new InvalidCoordinatesException(
                    "Coordinates must be valid: Latitude between -90 and 90, Longitude between -180 and 180.", e);
        }
    }

    /**
     * Validates datetime is within schedule validity period and sets default if null.
     *
     * @throws InvalidDateTimeException if datetime is outside validity period
     */
    public static LocalDateTime validateAndSetDefaultDateTime(@Nullable LocalDateTime dateTime,
                                                              ScheduleInformationService service) {
        dateTime = (dateTime == null) ? LocalDateTime.now() : dateTime;

        if (service.getValidity().isWithin(dateTime.toLocalDate())) {
            return dateTime;
        }

        LocalDateTime startDate = service.getValidity().getStartDate().atStartOfDay();
        LocalDateTime endDate = service.getValidity().getEndDate().atTime(23, 59, 59);

        throw new InvalidDateTimeException(String.format(
                "The provided datetime '%s' is outside of the schedule validity period. Please provide a datetime between '%s' and '%s'.",
                dateTime.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                startDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                endDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
    }

    /**
     * Validates routing query parameters against service capabilities.
     *
     * @throws UnsupportedRoutingFeatureException if a feature is not supported
     */
    public static void validateRoutingFeatureSupport(int maxWalkingDuration, int maxTransferNumber, int maxTravelTime,
                                                     int minTransferTime, boolean wheelchairAccessible,
                                                     boolean bikeAllowed, EnumSet<TravelMode> travelModes,
                                                     RoutingFeatures features) {
        if (maxWalkingDuration != Integer.MAX_VALUE && !features.supportsMaxWalkingDuration()) {
            throw new UnsupportedRoutingFeatureException(
                    "Max Walking Duration is not supported by the router of this service.");
        }
        if (maxTransferNumber != Integer.MAX_VALUE && !features.supportsMaxNumTransfers()) {
            throw new UnsupportedRoutingFeatureException(
                    "Max Transfer Number is not supported by the router of this service.");
        }
        if (maxTravelTime != Integer.MAX_VALUE && !features.supportsMaxTravelTime()) {
            throw new UnsupportedRoutingFeatureException(
                    "Max Travel Time is not supported by the router of this service.");
        }
        if (minTransferTime != 0 && !features.supportsMinTransferDuration()) {
            throw new UnsupportedRoutingFeatureException(
                    "Min Transfer Duration is not supported by the router of this service.");
        }
        if (wheelchairAccessible && !features.supportsAccessibility()) {
            throw new UnsupportedRoutingFeatureException(
                    "Wheelchair Accessible routing is not supported by the router of this service.");
        }
        if (bikeAllowed && !features.supportsBikes()) {
            throw new UnsupportedRoutingFeatureException(
                    "Bike friendly routing is not supported by the router of this service.");
        }
        if (!travelModes.containsAll(EnumSet.allOf(TravelMode.class)) && !features.supportsTravelModes()) {
            throw new UnsupportedRoutingFeatureException(
                    "Filtering travel modes is not supported by the router of this service.");
        }
    }

    /**
     * Validates that source and target stops are different.
     *
     * @throws InvalidRoutingParametersException if stops are the same
     */
    public static void validateStopsAreDifferent(String sourceStopId, String targetStopId) {
        if (sourceStopId.equals(targetStopId)) {
            throw new InvalidRoutingParametersException(
                    "The source stop ID and target stop ID cannot be the same. Please provide different stop IDs for the source and target.");
        }
    }

    /**
     * Validates that source and target coordinates are different.
     *
     * @throws InvalidRoutingParametersException if coordinates are the same
     */
    public static void validateCoordinatesAreDifferent(GeoCoordinate sourceCoordinate, GeoCoordinate targetCoordinate) {
        if (sourceCoordinate.equals(targetCoordinate)) {
            throw new InvalidRoutingParametersException(
                    "The source and target coordinates cannot be the same. Please provide different coordinates for the source and target.");
        }
    }

    /**
     * Validates that either stop ID or coordinates are provided, but not both.
     *
     * @throws InvalidRoutingParametersException if validation fails
     */
    public static void validateStopParametersMutualExclusivity(@Nullable String stopId, @Nullable Double latitude,
                                                               @Nullable Double longitude, String stopType) {
        if (stopId == null) {
            if (latitude == null || longitude == null) {
                throw new InvalidRoutingParametersException(
                        "Either " + stopType + "StopId or " + stopType + "Latitude and " + stopType + "Longitude must be provided.");
            }
        } else {
            if (latitude != null || longitude != null) {
                throw new InvalidRoutingParametersException(
                        "Only " + stopType + "StopId or " + stopType + "Latitude and " + stopType + "Longitude must be provided, but not both.");
            }
        }
    }

    /**
     * Validates that until datetime is after departure datetime.
     *
     * @throws InvalidRoutingParametersException if until datetime is before departure datetime
     */
    public static void validateUntilDateTime(LocalDateTime departureDateTime, @Nullable LocalDateTime untilDateTime) {
        if (untilDateTime != null && untilDateTime.isBefore(departureDateTime)) {
            throw new InvalidRoutingParametersException("Until date time must be after departure date time.");
        }
    }
}

