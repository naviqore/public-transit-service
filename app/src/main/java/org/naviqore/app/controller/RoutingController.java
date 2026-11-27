package org.naviqore.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.naviqore.app.config.NaviqoreProperties;
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

@Slf4j
@RestController
@RequestMapping("/routing")
@Tag(name = "routing", description = "APIs related to routing and connections")
@Validated
@RequiredArgsConstructor
public class RoutingController {

    private static final String DEFAULT_TIME_TYPE = "DEPARTURE";

    private final PublicTransitService service;
    private final NaviqoreProperties properties;

    @Operation(summary = "Get information about the routing", description = "Get all relevant information about the routing features supported by the service.")
    @ApiResponse(responseCode = "200", description = "A list of routing features supported by the service.")
    @GetMapping("")
    public RoutingInfo getRoutingInfo() {
        RoutingFeatures features = service.getRoutingFeatures();
        return new RoutingInfo(features.supportsMaximumTransfers(), features.supportsMaximumTravelDuration(),
                features.supportsMaximumWalkDuration(), features.supportsMinimumTransferDuration(),
                features.supportsWheelchairAccessible(), features.supportsBikeAllowed(),
                features.supportsTravelModes());
    }

    @Operation(summary = "Request connections between two stops or locations", description = "Requests connections between two stops or locations at a given departure / arrival datetime.")
    @ApiResponse(responseCode = "200", description = "A list of connections between the specified stops.")
    @ApiResponse(responseCode = "400", description = "Invalid input parameters (invalid-parameters, invalid-coordinates, invalid-datetime, unsupported-routing-feature, constraint-violation, type-mismatch, missing-request-parameter, malformed-request-body)", content = @Content(schema = @Schema))
    @ApiResponse(responseCode = "404", description = "Stop not found (stop-not-found)", content = @Content(schema = @Schema))
    @ApiResponse(responseCode = "500", description = "Internal server error (routing-error, internal-server-error)", content = @Content(schema = @Schema))
    @GetMapping("/connections")
    public List<Connection> getConnections(@RequestParam(required = false) @Parameter(description = "ID of the stop to leave from. Give either it or sourceLatitude and sourceLongitude.") String sourceStopId,
                                           @RequestParam(required = false) @Parameter(description = "Latitude of the location to leave from, in WGS84 degrees from -90 to 90.") Double sourceLatitude,
                                           @RequestParam(required = false) @Parameter(description = "Longitude of the location to leave from, in WGS84 degrees from -180 to 180.") Double sourceLongitude,
                                           @RequestParam(required = false) @Parameter(description = "ID of the stop to reach. Give either it or targetLatitude and targetLongitude.") String targetStopId,
                                           @RequestParam(required = false) @Parameter(description = "Latitude of the location to reach, in WGS84 degrees from -90 to 90.") Double targetLatitude,
                                           @RequestParam(required = false) @Parameter(description = "Longitude of the location to reach, in WGS84 degrees from -180 to 180.") Double targetLongitude,
                                           @RequestParam(required = false) @Parameter(description = Descriptions.DATE_TIME) OffsetDateTime dateTime,
                                           @RequestParam(required = false, defaultValue = DEFAULT_TIME_TYPE) @Parameter(description = Descriptions.TIME_TYPE) TimeType timeType,
                                           @RequestParam(required = false, defaultValue = "0") @Parameter(description = Descriptions.TIME_WINDOW_DURATION) @Min(0) Integer timeWindowDuration,
                                           @RequestParam(required = false) @Parameter(description = Descriptions.MAX_WALK_DURATION) @Min(0) Integer maxWalkDuration,
                                           @RequestParam(required = false) @Parameter(description = Descriptions.MAX_TRANSFERS) @Min(0) Integer maxTransfers,
                                           @RequestParam(required = false) @Parameter(description = Descriptions.MAX_TRAVEL_DURATION) @Min(1) Integer maxTravelDuration,
                                           @RequestParam(required = false, defaultValue = "0") @Parameter(description = Descriptions.MIN_TRANSFER_DURATION) @Min(0) Integer minTransferDuration,
                                           @RequestParam(required = false, defaultValue = "false") @Parameter(description = Descriptions.WHEELCHAIR_ACCESSIBLE) boolean wheelchairAccessible,
                                           @RequestParam(required = false, defaultValue = "false") @Parameter(description = Descriptions.BIKE_ALLOWED) boolean bikeAllowed,
                                           @RequestParam(required = false) @Parameter(description = Descriptions.TRAVEL_MODES) EnumSet<TravelMode> travelModes) throws ConnectionRoutingException {
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
        ConnectionQueryConfig config = Utils.createConfig(timeWindowDuration, maxWalkDuration, maxTransfers,
                maxTravelDuration, minTransferDuration, wheelchairAccessible, bikeAllowed, travelModes, service,
                properties.getRouting());

        // determine routing case and get connections
        if (sourceStop != null && targetStop != null) {
            RequestValidator.validateStopsAreDifferent(sourceStopId, targetStopId);
            return map(service.getConnections(sourceStop, targetStop, dateTime, map(timeType), config));
        } else if (sourceStop != null) {
            assert targetCoordinate != null; // validated
            return map(service.getConnections(sourceStop, targetCoordinate, dateTime, map(timeType), config));
        } else if (targetStop != null) {
            assert sourceCoordinate != null; // validated
            return map(service.getConnections(sourceCoordinate, targetStop, dateTime, map(timeType), config));
        } else {
            assert sourceCoordinate != null && targetCoordinate != null; // validated
            RequestValidator.validateCoordinatesAreDifferent(sourceCoordinate, targetCoordinate);
            return map(service.getConnections(sourceCoordinate, targetCoordinate, dateTime, map(timeType), config));
        }
    }

    @Operation(summary = "Request a list of fastest connections to each reachable stop", description = "Request a list of fastest connections to each reachable stop from a specified stop or location at a given departure / arrival datetime.")
    @ApiResponse(responseCode = "200", description = "A list of stop and fastest connection pairs for each reachable stop.")
    @ApiResponse(responseCode = "400", description = "Invalid input parameters (invalid-parameters, invalid-coordinates, invalid-datetime, unsupported-routing-feature, constraint-violation, type-mismatch, missing-request-parameter, malformed-request-body)", content = @Content(schema = @Schema))
    @ApiResponse(responseCode = "404", description = "Stop not found (stop-not-found)", content = @Content(schema = @Schema))
    @ApiResponse(responseCode = "500", description = "Internal server error (routing-error, internal-server-error)", content = @Content(schema = @Schema))
    @GetMapping("/isolines")
    public List<StopConnection> getIsolines(@RequestParam(required = false) @Parameter(description = "ID of the stop the connections leave from, or arrive at for timeType ARRIVAL. Give either it or sourceLatitude and sourceLongitude.") String sourceStopId,
                                            @RequestParam(required = false) @Parameter(description = "Latitude of the location the connections leave from (arrive at), in WGS84 degrees from -90 to 90.") Double sourceLatitude,
                                            @RequestParam(required = false) @Parameter(description = "Longitude of the location the connections leave from (arrive at), in WGS84 degrees from -180 to 180.") Double sourceLongitude,
                                            @RequestParam(required = false) @Parameter(description = Descriptions.DATE_TIME) OffsetDateTime dateTime,
                                            @RequestParam(required = false, defaultValue = DEFAULT_TIME_TYPE) @Parameter(description = Descriptions.TIME_TYPE) TimeType timeType,
                                            @RequestParam(required = false, defaultValue = "0") @Parameter(description = Descriptions.TIME_WINDOW_DURATION) @Min(0) Integer timeWindowDuration,
                                            @RequestParam(required = false) @Parameter(description = Descriptions.MAX_WALK_DURATION) @Min(0) Integer maxWalkDuration,
                                            @RequestParam(required = false) @Parameter(description = Descriptions.MAX_TRANSFERS) @Min(0) Integer maxTransfers,
                                            @RequestParam(required = false) @Parameter(description = Descriptions.MAX_TRAVEL_DURATION) @Min(1) Integer maxTravelDuration,
                                            @RequestParam(required = false, defaultValue = "0") @Parameter(description = Descriptions.MIN_TRANSFER_DURATION) @Min(0) Integer minTransferDuration,
                                            @RequestParam(required = false, defaultValue = "false") @Parameter(description = Descriptions.WHEELCHAIR_ACCESSIBLE) boolean wheelchairAccessible,
                                            @RequestParam(required = false, defaultValue = "false") @Parameter(description = Descriptions.BIKE_ALLOWED) boolean bikeAllowed,
                                            @RequestParam(required = false) @Parameter(description = Descriptions.TRAVEL_MODES) EnumSet<TravelMode> travelModes,
                                            @RequestParam(required = false, defaultValue = "false") @Parameter(description = "Whether to add the whole connection to each stop, and the stop times to the trip of its connecting leg.") boolean detailed) throws ConnectionRoutingException {

        // get stops or coordinates if available
        GeoCoordinate sourceCoordinate = RequestValidator.getCoordinateIfAvailable(sourceStopId, sourceLatitude,
                sourceLongitude, StopType.SOURCE);
        Stop sourceStop = RequestValidator.getStopIfAvailable(sourceStopId, service, StopType.SOURCE);

        // configure routing request
        dateTime = RequestValidator.validateAndSetDefaultDateTime(dateTime, service);
        ConnectionQueryConfig config = Utils.createConfig(timeWindowDuration, maxWalkDuration, maxTransfers,
                maxTravelDuration, minTransferDuration, wheelchairAccessible, bikeAllowed, travelModes, service,
                properties.getRouting());

        // determine routing case and get isolines
        if (sourceStop != null) {
            return map(service.getIsolines(sourceStop, dateTime, map(timeType), config), timeType, detailed);
        } else {
            assert sourceCoordinate != null; // validated
            return map(service.getIsolines(sourceCoordinate, dateTime, map(timeType), config), timeType, detailed);
        }
    }

    private static class Utils {

        private static ConnectionQueryConfig createConfig(Integer timeWindowDuration, @Nullable Integer maxWalkDuration,
                                                          @Nullable Integer maxTransfers,
                                                          @Nullable Integer maxTravelDuration,
                                                          Integer minTransferDuration, boolean wheelchairAccessible,
                                                          boolean bikeAllowed,
                                                          @Nullable EnumSet<TravelMode> travelModes,
                                                          PublicTransitService service,
                                                          NaviqoreProperties.Routing routing) {

            RequestValidator.validateTimeWindowDuration(timeWindowDuration, routing.getTimeWindowDurationMaximum());

            // replace null values with default value
            int maximumWalkDuration = java.util.Optional.ofNullable(maxWalkDuration).orElse(Integer.MAX_VALUE);
            int maximumTransfers = java.util.Optional.ofNullable(maxTransfers).orElse(Integer.MAX_VALUE);
            int maximumTravelDuration = java.util.Optional.ofNullable(maxTravelDuration).orElse(Integer.MAX_VALUE);

            if (travelModes == null || travelModes.isEmpty()) {
                travelModes = EnumSet.allOf(TravelMode.class);
            }

            // validate feature support
            RequestValidator.validateRoutingFeatureSupport(maximumWalkDuration, maximumTransfers, maximumTravelDuration,
                    minTransferDuration, wheelchairAccessible, bikeAllowed, travelModes, service.getRoutingFeatures());

            return ConnectionQueryConfig.builder()
                    .timeWindowDuration(timeWindowDuration)
                    .maximumWalkDuration(maximumWalkDuration)
                    .minimumTransferDuration(minTransferDuration)
                    .maximumTransfers(maximumTransfers)
                    .maximumTravelDuration(maximumTravelDuration)
                    .wheelchairAccessible(wheelchairAccessible)
                    .bikeAllowed(bikeAllowed)
                    .travelModes(map(travelModes))
                    .build();
        }
    }

    /**
     * Descriptions of the parameters that connections and isolines share.
     */
    private static final class Descriptions {

        private static final String DATE_TIME = "Time to leave at or after, or for timeType ARRIVAL to arrive at or before, as an ISO-8601 date-time with offset, e.g. 2026-03-01T08:00:00+01:00 (+ as %2B in a URL). Now if omitted; its date must lie within the validity of the schedule.";
        private static final String TIME_TYPE = "Whether dateTime is a departure (DEPARTURE) or an arrival (ARRIVAL).";
        private static final String TIME_WINDOW_DURATION = "Time window in seconds after dateTime, or before it for timeType ARRIVAL, in which the connections leave (arrive); 0 for none. A window longer than naviqore.routing.time-window-duration-maximum, a day by default, is refused with 400.";
        private static final String MAX_WALK_DURATION = "Longest walk in seconds, between stops and to and from a location. No limit if omitted.";
        private static final String MAX_TRANSFERS = "Most transfers, which is the number of rides less one. No limit if omitted.";
        private static final String MAX_TRAVEL_DURATION = "Longest travel time in seconds, from leaving the origin to reaching the destination. No limit if omitted.";
        private static final String MIN_TRANSFER_DURATION = "Shortest transfer in seconds: a change at a stop takes at least this long, a walk between stops this much longer.";
        private static final String WHEELCHAIR_ACCESSIBLE = "Only trips accessible by wheelchair. Refused with 400 if the schedule does not tell, see supportsWheelchairAccessible of GET /routing.";
        private static final String BIKE_ALLOWED = "Only trips that allow bikes. Refused with 400 if the schedule does not tell, see supportsBikeAllowed of GET /routing.";
        private static final String TRAVEL_MODES = "Only trips of these travel modes, comma-separated or repeated. All if omitted or empty.";
    }
}