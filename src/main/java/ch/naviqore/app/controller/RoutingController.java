package ch.naviqore.app.controller;

import ch.naviqore.app.dto.Connection;
import ch.naviqore.app.dto.RoutingInfo;
import ch.naviqore.app.dto.StopConnection;
import ch.naviqore.app.dto.TimeType;
import ch.naviqore.app.dto.TravelMode;
import ch.naviqore.service.*;
import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.service.exception.ConnectionRoutingException;
import ch.naviqore.utils.spatial.GeoCoordinate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

import static ch.naviqore.app.dto.DtoMapper.map;

@Slf4j
@RestController
@RequestMapping("/routing")
@Tag(name = "routing", description = "APIs related to routing and connections")
public class RoutingController {

    private final PublicTransitService service;

    @Autowired
    public RoutingController(PublicTransitService service) {
        this.service = service;
    }

    private static void handleConnectionRoutingException(ConnectionRoutingException e) {
        log.error("Connection routing exception", e);
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    @Operation(summary = "Get information about the routing", description = "Get all relevant information about the routing features supported by the service.")
    @ApiResponse(responseCode = "200", description = "A list of routing features supported by the service.")
    @GetMapping("/")
    public RoutingInfo getRoutingInfo() {
        SupportedRoutingFeatures features = service.getSupportedRoutingFeatures();
        return new RoutingInfo(features.supportsMaxNumTransfers(), features.supportsMaxTravelTime(),
                features.supportsMaxWalkingDuration(), features.supportsMinTransferDuration(), features.supportsAccessibility(),
                features.supportsBikes(), features.supportsTravelModes());
    }

    @Operation(summary = "Request connections between two stops or locations", description = "Requests connections between two stops or locations at a given departure / arrival datetime.")
    @ApiResponse(responseCode = "200", description = "A list of connections between the specified stops.")
    @ApiResponse(responseCode = "400", description = "Invalid input parameters", content = @Content(schema = @Schema()))
    @ApiResponse(responseCode = "404", description = "StopID does not exist", content = @Content(schema = @Schema()))
    @GetMapping("/connections")
    public List<Connection> getConnections(@RequestParam(required = false) String sourceStopId,
                                           @RequestParam(required = false) Double sourceLatitude,
                                           @RequestParam(required = false) Double sourceLongitude,
                                           @RequestParam(required = false) String targetStopId,
                                           @RequestParam(required = false) Double targetLatitude,
                                           @RequestParam(required = false) Double targetLongitude,
                                           @RequestParam(required = false) LocalDateTime dateTime,
                                           @RequestParam(required = false, defaultValue = "DEPARTURE") TimeType timeType,
                                           @RequestParam(required = false) Integer maxWalkingDuration,
                                           @RequestParam(required = false) Integer maxTransferNumber,
                                           @RequestParam(required = false) Integer maxTravelTime,
                                           @RequestParam(required = false, defaultValue = "0") int minTransferTime,
                                           @RequestParam(required = false, defaultValue = "false") boolean wheelchairAccessible,
                                           @RequestParam(required = false, defaultValue = "false") boolean bikeAllowed,
                                           @RequestParam(required = false) EnumSet<TravelMode> travelModes) {
        // get coordinates if available
        GeoCoordinate sourceCoordinate = Utils.getCoordinateIfAvailable(sourceStopId, sourceLatitude, sourceLongitude,
                GlobalValidator.StopType.SOURCE);
        GeoCoordinate targetCoordinate = Utils.getCoordinateIfAvailable(targetStopId, targetLatitude, targetLongitude,
                GlobalValidator.StopType.TARGET);

        // get stops if available
        Stop sourceStop = Utils.getStopIfAvailable(sourceStopId, service, GlobalValidator.StopType.SOURCE);
        Stop targetStop = Utils.getStopIfAvailable(targetStopId, service, GlobalValidator.StopType.TARGET);

        // configure routing request
        dateTime = GlobalValidator.validateAndSetDefaultDateTime(dateTime, service);
        ConnectionQueryConfig config = Utils.createConfig(maxWalkingDuration, maxTransferNumber, maxTravelTime,
                minTransferTime, wheelchairAccessible, bikeAllowed, travelModes, service);
        // determine routing case and get connections
        try {
            if (sourceStop != null && targetStop != null) {
                RoutingRequestValidator.validateStops(sourceStopId, targetStopId);
                return map(service.getConnections(sourceStop, targetStop, dateTime, map(timeType), config));
            } else if (sourceStop != null) {
                return map(service.getConnections(sourceStop, targetCoordinate, dateTime, map(timeType), config));
            } else if (targetStop != null) {
                return map(service.getConnections(sourceCoordinate, targetStop, dateTime, map(timeType), config));
            } else {
                RoutingRequestValidator.validateCoordinates(sourceCoordinate, targetCoordinate);
                return map(service.getConnections(sourceCoordinate, targetCoordinate, dateTime, map(timeType), config));
            }
        } catch (ConnectionRoutingException e) {
            handleConnectionRoutingException(e);
            return null;
        }
    }

    @Operation(summary = "Request a list of fastest connections to each reachable stop", description = "Request a list of fastest connections to each reachable stop from a specified stop or location at a given departure / arrival datetime.")
    @ApiResponse(responseCode = "200", description = "A list of stop and fastest connection pairs for each reachable stop.")
    @ApiResponse(responseCode = "400", description = "Invalid input parameters", content = @Content(schema = @Schema()))
    @ApiResponse(responseCode = "404", description = "StopID does not exist", content = @Content(schema = @Schema()))
    @GetMapping("/isolines")
    public List<StopConnection> getIsolines(@RequestParam(required = false) String sourceStopId,
                                            @RequestParam(required = false) Double sourceLatitude,
                                            @RequestParam(required = false) Double sourceLongitude,
                                            @RequestParam(required = false) LocalDateTime dateTime,
                                            @RequestParam(required = false, defaultValue = "DEPARTURE") TimeType timeType,
                                            @RequestParam(required = false) Integer maxWalkingDuration,
                                            @RequestParam(required = false) Integer maxTransferNumber,
                                            @RequestParam(required = false) Integer maxTravelTime,
                                            @RequestParam(required = false, defaultValue = "0") int minTransferTime,
                                            @RequestParam(required = false, defaultValue = "false") boolean wheelchairAccessible,
                                            @RequestParam(required = false, defaultValue = "false") boolean bikeAllowed,
                                            @RequestParam(required = false) EnumSet<TravelMode> travelModes,
                                            @RequestParam(required = false, defaultValue = "false") boolean returnConnections) {

        // get stops or coordinates if available
        GeoCoordinate sourceCoordinate = Utils.getCoordinateIfAvailable(sourceStopId, sourceLatitude, sourceLongitude,
                GlobalValidator.StopType.SOURCE);
        Stop sourceStop = Utils.getStopIfAvailable(sourceStopId, service, GlobalValidator.StopType.SOURCE);

        // configure routing request
        dateTime = GlobalValidator.validateAndSetDefaultDateTime(dateTime, service);
        ConnectionQueryConfig config = Utils.createConfig(maxWalkingDuration, maxTransferNumber, maxTravelTime,
                minTransferTime, wheelchairAccessible, bikeAllowed, travelModes, service);

        // determine routing case and get isolines
        try {
            if (sourceStop != null) {
                return map(service.getIsoLines(sourceStop, dateTime, map(timeType), config), timeType,
                        returnConnections);
            } else {
                return map(service.getIsoLines(sourceCoordinate, dateTime, map(timeType), config), timeType,
                        returnConnections);
            }
        } catch (ConnectionRoutingException e) {
            handleConnectionRoutingException(e);
            return null;
        }
    }

    private static class Utils {

        private static @Nullable GeoCoordinate getCoordinateIfAvailable(@Nullable String stopId,
                                                                        @Nullable Double latitude,
                                                                        @Nullable Double longitude,
                                                                        GlobalValidator.StopType stopType) {
            RoutingRequestValidator.validateStopParameters(stopId, latitude, longitude, stopType);
            return stopId == null ? RoutingRequestValidator.validateCoordinate(latitude, longitude) : null;
        }

        private static @Nullable Stop getStopIfAvailable(@Nullable String stopId, ScheduleInformationService service,
                                                         GlobalValidator.StopType stopType) {
            return stopId != null ? GlobalValidator.validateAndGetStop(stopId, service, stopType) : null;
        }

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

            // validate and create config
            RoutingRequestValidator.validateQueryParams(maxWalkingDuration, maxTransferNumber, maxTravelTime,
                    minTransferTime, wheelchairAccessible, bikeAllowed, travelModes, service);
            return new ConnectionQueryConfig(maxWalkingDuration, minTransferTime, maxTransferNumber, maxTravelTime,
                    wheelchairAccessible, bikeAllowed, map(travelModes));
        }

        private static int setToMaxIfNull(Integer value) {
            return (value == null) ? Integer.MAX_VALUE : value;
        }

    }

}



