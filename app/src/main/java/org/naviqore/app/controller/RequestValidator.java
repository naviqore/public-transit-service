package org.naviqore.app.controller;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.naviqore.app.dto.StopType;
import org.naviqore.app.dto.TravelMode;
import org.naviqore.app.exception.*;
import org.naviqore.service.PublicTransitService;
import org.naviqore.service.RoutingFeatures;
import org.naviqore.service.ScheduleInformationService;
import org.naviqore.service.Stop;
import org.naviqore.utils.spatial.GeoCoordinate;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;

/**
 * Validator for all REST API request parameters and business rules.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RequestValidator {

    /**
     * Creates and validates a GeoCoordinate from latitude and longitude.
     *
     * @throws InvalidCoordinatesException if coordinates are invalid
     */
    static GeoCoordinate createCoordinate(double latitude, double longitude) {
        try {
            return new GeoCoordinate(latitude, longitude);
        } catch (IllegalArgumentException e) {
            throw new InvalidCoordinatesException(
                    "Invalid coordinates. Latitude must be between -90 and 90, longitude must be between -180 and 180.",
                    e);
        }
    }

    /**
     * Validates and retrieves a coordinate from latitude/longitude parameters if stop ID is not provided.
     *
     * @return GeoCoordinate if stop ID is null and coordinates are valid, null if stop ID is provided
     * @throws InvalidParametersException  if parameter combination is invalid
     * @throws InvalidCoordinatesException if coordinates are invalid
     */
    static @Nullable GeoCoordinate getCoordinateIfAvailable(@Nullable String stopId, @Nullable Double latitude,
                                                            @Nullable Double longitude, StopType stopType) {
        validateStopParametersMutualExclusivity(stopId, latitude, longitude, stopType);
        return stopId == null ? createCoordinate(latitude, longitude) : null;
    }

    /**
     * Validates that source and target coordinates are different.
     *
     * @throws InvalidParametersException if coordinates are the same
     */
    static void validateCoordinatesAreDifferent(GeoCoordinate sourceCoordinate, GeoCoordinate targetCoordinate) {
        if (sourceCoordinate.equals(targetCoordinate)) {
            throw new InvalidParametersException(
                    "Source and target coordinates cannot be the same. Please provide different coordinates.");
        }
    }

    /**
     * Validates datetime is within schedule validity period and sets default if null.
     *
     * @return validated datetime (or current datetime if null)
     * @throws InvalidDateTimeException if datetime is outside validity period
     */
    static OffsetDateTime validateAndSetDefaultDateTime(@Nullable OffsetDateTime dateTime,
                                                        ScheduleInformationService service) {
        dateTime = (dateTime == null) ? OffsetDateTime.now() : dateTime;

        if (service.getValidity().isWithin(dateTime.toLocalDate())) {
            return dateTime;
        }

        LocalDateTime startDate = service.getValidity().getStartDate().atStartOfDay();
        LocalDateTime endDate = service.getValidity().getEndDate().atTime(23, 59, 59);

        throw new InvalidDateTimeException(
                String.format("DateTime '%s' is outside the schedule validity period. Valid range is '%s' to '%s'.",
                        dateTime.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        startDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        endDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
    }

    /**
     * Validates that until datetime is after from datetime.
     *
     * @throws InvalidParametersException if until datetime is before from datetime
     */
    static void validateTimeWindow(OffsetDateTime from, OffsetDateTime until) {
        if (until.isBefore(from)) {
            throw new InvalidParametersException("Until date time must be after from date time.");
        }
    }

    /**
     * Validates and retrieves a stop by ID if provided.
     *
     * @return Stop if stop ID is provided and exists, null if stop ID is null
     * @throws StopNotFoundException if stop ID is provided but not found
     */
    static @Nullable Stop getStopIfAvailable(@Nullable String stopId, PublicTransitService service, StopType stopType) {
        if (stopId == null) {
            return null;
        }
        try {
            return service.getStopById(stopId);
        } catch (org.naviqore.service.exception.StopNotFoundException e) {
            throw new StopNotFoundException(stopId, stopType);
        }
    }

    /**
     * Retrieves a stop by ID (for schedule endpoints).
     *
     * @throws StopNotFoundException if stop ID is not found
     */
    static Stop getStopById(String stopId, ScheduleInformationService service) {
        try {
            return service.getStopById(stopId);
        } catch (org.naviqore.service.exception.StopNotFoundException e) {
            throw new StopNotFoundException(stopId, null);
        }
    }

    /**
     * Validates that source and target stops are different.
     *
     * @throws InvalidParametersException if stops are the same
     */
    static void validateStopsAreDifferent(String sourceStopId, String targetStopId) {
        if (sourceStopId.equals(targetStopId)) {
            throw new InvalidParametersException(
                    "Source and target stop cannot be the same. Please provide different stops.");
        }
    }

    /**
     * Validates routing query parameters against service capabilities.
     *
     * @throws UnsupportedRoutingFeatureException if a requested feature is not supported
     */
    static void validateRoutingFeatureSupport(int maxWalkDuration, int maxTransfers, int maxTravelDuration,
                                              int minTransferDuration, boolean wheelchairAccessible,
                                              boolean bikeAllowed, EnumSet<TravelMode> travelModes,
                                              RoutingFeatures features) {
        if (maxWalkDuration != Integer.MAX_VALUE && !features.supportsMaxWalkDuration()) {
            throw new UnsupportedRoutingFeatureException(
                    "Maximum walk duration parameter is not supported by this service.");
        }
        if (maxTransfers != Integer.MAX_VALUE && !features.supportsMaxTransfers()) {
            throw new UnsupportedRoutingFeatureException(
                    "Maximum transfers parameter is not supported by this service.");
        }
        if (maxTravelDuration != Integer.MAX_VALUE && !features.supportsMaxTravelDuration()) {
            throw new UnsupportedRoutingFeatureException(
                    "Maximum travel duration parameter is not supported by this service.");
        }
        if (minTransferDuration != 0 && !features.supportsMinTransferDuration()) {
            throw new UnsupportedRoutingFeatureException(
                    "Minimum transfer duration parameter is not supported by this service.");
        }
        if (wheelchairAccessible && !features.supportsAccessibility()) {
            throw new UnsupportedRoutingFeatureException(
                    "Wheelchair accessibility parameter is not supported by this service.");
        }
        if (bikeAllowed && !features.supportsBikes()) {
            throw new UnsupportedRoutingFeatureException(
                    "Bike-friendly routing parameter is not supported by this service.");
        }
        if (!travelModes.containsAll(EnumSet.allOf(TravelMode.class)) && !features.supportsTravelModes()) {
            throw new UnsupportedRoutingFeatureException(
                    "Travel mode filtering parameter is not supported by this service.");
        }
    }

    /**
     * Validates that either stop ID or coordinates are provided, but not both.
     *
     * @throws InvalidParametersException if validation fails
     */
    private static void validateStopParametersMutualExclusivity(@Nullable String stopId, @Nullable Double latitude,
                                                                @Nullable Double longitude, StopType stopType) {
        String stopTypeLabel = stopType.name().toLowerCase();
        if (stopId == null) {
            if (latitude == null || longitude == null) {
                throw new InvalidParametersException(
                        String.format("Either %sStopId or both %sLatitude and %sLongitude must be provided.",
                                stopTypeLabel, stopTypeLabel, stopTypeLabel));
            }
        } else {
            if (latitude != null || longitude != null) {
                throw new InvalidParametersException(String.format(
                        "Provide either %sStopId or coordinates (%sLatitude and %sLongitude), but not both.",
                        stopTypeLabel, stopTypeLabel, stopTypeLabel));
            }
        }
    }
}

