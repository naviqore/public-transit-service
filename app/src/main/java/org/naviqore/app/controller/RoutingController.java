package org.naviqore.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.naviqore.app.dto.*;
import org.naviqore.service.PublicTransitService;
import org.naviqore.service.RoutingFeatures;
import org.naviqore.service.Stop;
import org.naviqore.service.config.ConnectionQueryConfig;
import org.naviqore.service.exception.ConnectionRoutingException;
import org.naviqore.utils.spatial.GeoCoordinate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;

import static org.naviqore.app.dto.DtoMapper.map;

// TODO: debug: Parent8591056 --> Parent8507000 at 2025-03-30T15:00:00Z, gives the same as 2025-03-31T15:00:00Z
//  suspect its coming from caching.

@Slf4j
@RestController
@RequestMapping("/routing")
@Tag(name = "routing", description = "APIs related to routing and connections")
@Validated
@RequiredArgsConstructor
public class RoutingController {

    private static final String DEFAULT_TIME_TYPE = "DEPARTURE";
    private static final String DEFAULT_MIN_TRANSFER_TIME = "0";

    private final PublicTransitService service;

    @Operation(summary = "Get information about the routing", description = "Get all relevant information about the routing features supported by the service.")
    @ApiResponse(responseCode = "200", description = "A list of routing features supported by the service.")
    @GetMapping("")
    public RoutingInfo getRoutingInfo() {
        RoutingFeatures features = service.getRoutingFeatures();
        return new RoutingInfo(features.supportsMaxNumTransfers(), features.supportsMaxTravelTime(),
                features.supportsMaxWalkingDuration(), features.supportsMinTransferDuration(),
                features.supportsAccessibility(), features.supportsBikes(), features.supportsTravelModes());
    }

    @Operation(summary = "Request connections between two stops or locations", description = "Requests connections between two stops or locations at a given departure / arrival datetime.")
    @ApiResponse(responseCode = "200", description = "A list of connections between the specified stops.")
    @ApiResponse(responseCode = "400", description = "Invalid input parameters (invalid-parameters, invalid-coordinates, invalid-datetime, unsupported-routing-feature, constraint-violation, type-mismatch, missing-request-parameter, malformed-request-body)", content = @Content(schema = @Schema()))
    @ApiResponse(responseCode = "404", description = "Stop not found (stop-not-found)", content = @Content(schema = @Schema()))
    @ApiResponse(responseCode = "500", description = "Internal server error (routing-error, internal-server-error)", content = @Content(schema = @Schema()))
    @GetMapping("/connections")
    public List<Connection> getConnections(@RequestParam(required = false) String sourceStopId,
                                           @RequestParam(required = false) Double sourceLatitude,
                                           @RequestParam(required = false) Double sourceLongitude,
                                           @RequestParam(required = false) String targetStopId,
                                           @RequestParam(required = false) Double targetLatitude,
                                           @RequestParam(required = false) Double targetLongitude,
                                           @RequestParam(required = false) OffsetDateTime dateTime,
                                           @RequestParam(required = false, defaultValue = DEFAULT_TIME_TYPE) TimeType timeType,
                                           @RequestParam(required = false) @Min(0) Integer maxWalkingDuration,
                                           @RequestParam(required = false) @Min(0) Integer maxTransferNumber,
                                           @RequestParam(required = false) @Min(1) Integer maxTravelTime,
                                           @RequestParam(required = false, defaultValue = DEFAULT_MIN_TRANSFER_TIME) @Min(0) int minTransferTime,
                                           @RequestParam(required = false, defaultValue = "false") boolean wheelchairAccessible,
                                           @RequestParam(required = false, defaultValue = "false") boolean bikeAllowed,
                                           @RequestParam(required = false) EnumSet<TravelMode> travelModes) throws ConnectionRoutingException {
        // get coordinates if available
        GeoCoordinate sourceCoordinate = RequestValidator.getCoordinateIfAvailable(sourceStopId, sourceLatitude,
                sourceLongitude, StopType.SOURCE);
        GeoCoordinate targetCoordinate = RequestValidator.getCoordinateIfAvailable(targetStopId, targetLatitude,
                targetLongitude, StopType.TARGET);

        // get stops if available
        Stop sourceStop = RequestValidator.getStopIfAvailable(sourceStopId, service, StopType.SOURCE);
        Stop targetStop = RequestValidator.getStopIfAvailable(targetStopId, service, StopType.TARGET);

        // configure routing request
        dateTime = RequestValidator.validateAndSetDefaultDateTime(dateTime, service);
        ConnectionQueryConfig config = Utils.createConfig(maxWalkingDuration, maxTransferNumber, maxTravelTime,
                minTransferTime, wheelchairAccessible, bikeAllowed, travelModes, service);

        // determine routing case and get connections
        if (sourceStop != null && targetStop != null) {
            RequestValidator.validateStopsAreDifferent(sourceStopId, targetStopId);
            return map(service.getConnections(sourceStop, targetStop, dateTime, map(timeType), config));
        } else if (sourceStop != null) {
            return map(service.getConnections(sourceStop, targetCoordinate, dateTime, map(timeType), config));
        } else if (targetStop != null) {
            return map(service.getConnections(sourceCoordinate, targetStop, dateTime, map(timeType), config));
        } else {
            assert sourceCoordinate != null; // never happens
            RequestValidator.validateCoordinatesAreDifferent(sourceCoordinate, targetCoordinate);
            return map(service.getConnections(sourceCoordinate, targetCoordinate, dateTime, map(timeType), config));
        }
    }

    @Operation(summary = "Request a list of fastest connections to each reachable stop", description = "Request a list of fastest connections to each reachable stop from a specified stop or location at a given departure / arrival datetime.")
    @ApiResponse(responseCode = "200", description = "A list of stop and fastest connection pairs for each reachable stop.")
    @ApiResponse(responseCode = "400", description = "Invalid input parameters (invalid-parameters, invalid-coordinates, invalid-datetime, unsupported-routing-feature, constraint-violation, type-mismatch, missing-request-parameter, malformed-request-body)", content = @Content(schema = @Schema()))
    @ApiResponse(responseCode = "404", description = "Stop not found (stop-not-found)", content = @Content(schema = @Schema()))
    @ApiResponse(responseCode = "500", description = "Internal server error (routing-error, internal-server-error)", content = @Content(schema = @Schema()))
    @GetMapping("/isolines")
    public List<StopConnection> getIsolines(@RequestParam(required = false) String sourceStopId,
                                            @RequestParam(required = false) Double sourceLatitude,
                                            @RequestParam(required = false) Double sourceLongitude,
                                            @RequestParam(required = false) OffsetDateTime dateTime,
                                            @RequestParam(required = false, defaultValue = DEFAULT_TIME_TYPE) TimeType timeType,
                                            @RequestParam(required = false) @Min(0) Integer maxWalkingDuration,
                                            @RequestParam(required = false) @Min(0) Integer maxTransferNumber,
                                            @RequestParam(required = false) @Min(1) Integer maxTravelTime,
                                            @RequestParam(required = false, defaultValue = DEFAULT_MIN_TRANSFER_TIME) @Min(0) int minTransferTime,
                                            @RequestParam(required = false, defaultValue = "false") boolean wheelchairAccessible,
                                            @RequestParam(required = false, defaultValue = "false") boolean bikeAllowed,
                                            @RequestParam(required = false) EnumSet<TravelMode> travelModes,
                                            @RequestParam(required = false, defaultValue = "false") boolean returnConnections) throws ConnectionRoutingException {

        // get stops or coordinates if available
        GeoCoordinate sourceCoordinate = RequestValidator.getCoordinateIfAvailable(sourceStopId, sourceLatitude,
                sourceLongitude, StopType.SOURCE);
        Stop sourceStop = RequestValidator.getStopIfAvailable(sourceStopId, service, StopType.SOURCE);

        // configure routing request
        dateTime = RequestValidator.validateAndSetDefaultDateTime(dateTime, service);
        ConnectionQueryConfig config = Utils.createConfig(maxWalkingDuration, maxTransferNumber, maxTravelTime,
                minTransferTime, wheelchairAccessible, bikeAllowed, travelModes, service);

        // determine routing case and get isolines
        if (sourceStop != null) {
            return map(service.getIsolines(sourceStop, dateTime, map(timeType), config), timeType, returnConnections);
        } else {
            return map(service.getIsolines(sourceCoordinate, dateTime, map(timeType), config), timeType,
                    returnConnections);
        }
    }

    private static class Utils {

        private static ConnectionQueryConfig createConfig(@Nullable Integer maxWalkingDuration,
                                                          @Nullable Integer maxTransferNumber,
                                                          @Nullable Integer maxTravelTime, int minTransferTime,
                                                          boolean wheelchairAccessible, boolean bikeAllowed,
                                                          @Nullable EnumSet<TravelMode> travelModes,
                                                          PublicTransitService service) {

            // replace null values with integer max value
            maxWalkingDuration = setToMaxIfNull(maxWalkingDuration);
            maxTransferNumber = setToMaxIfNull(maxTransferNumber);
            maxTravelTime = setToMaxIfNull(maxTravelTime);

            if (travelModes == null || travelModes.isEmpty()) {
                travelModes = EnumSet.allOf(TravelMode.class);
            }

            // validate feature support
            RequestValidator.validateRoutingFeatureSupport(maxWalkingDuration, maxTransferNumber, maxTravelTime,
                    minTransferTime, wheelchairAccessible, bikeAllowed, travelModes, service.getRoutingFeatures());

            return ConnectionQueryConfig.builder()
                    .maximumWalkingDuration(maxWalkingDuration)
                    .minimumTransferDuration(minTransferTime)
                    .maximumTransferNumber(maxTransferNumber)
                    .maximumTravelTime(maxTravelTime)
                    .wheelchairAccessible(wheelchairAccessible)
                    .bikeAllowed(bikeAllowed)
                    .travelModes(map(travelModes))
                    .build();
        }

        private static int setToMaxIfNull(Integer value) {
            return (value == null) ? Integer.MAX_VALUE : value;
        }
    }
}