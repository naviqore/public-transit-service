package ch.naviqore.app.controller;

import ch.naviqore.app.dto.Connection;
import ch.naviqore.app.dto.StopConnection;
import ch.naviqore.app.dto.TimeType;
import ch.naviqore.service.PublicTransitService;
import ch.naviqore.service.ScheduleInformationService;
import ch.naviqore.service.Stop;
import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.utils.spatial.GeoCoordinate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

import static ch.naviqore.app.dto.DtoMapper.map;

@RestController
@RequestMapping("/routing")
@Tag(name = "routing", description = "APIs related to routing and connections")
public class RoutingController {

    private final PublicTransitService service;

    @Autowired
    public RoutingController(PublicTransitService service) {
        this.service = service;
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
                                           @RequestParam(required = false, defaultValue = "2147483647") int maxWalkingDuration,
                                           @RequestParam(required = false, defaultValue = "2147483647") int maxTransferNumber,
                                           @RequestParam(required = false, defaultValue = "2147483647") int maxTravelTime,
                                           @RequestParam(required = false, defaultValue = "0") int minTransferTime) {

        GeoCoordinate sourceCoordinate = Utils.getCoordinateIfAvailable(sourceStopId, sourceLatitude, sourceLongitude,
                GlobalStopType.SOURCE);
        GeoCoordinate targetCoordinate = Utils.getCoordinateIfAvailable(targetStopId, targetLatitude, targetLongitude,
                GlobalStopType.TARGET);
        Stop sourceStop = Utils.getStopIfAvailable(sourceStopId, service, GlobalStopType.SOURCE);
        Stop targetStop = Utils.getStopIfAvailable(targetStopId, service, GlobalStopType.TARGET);
        dateTime = Utils.setToNowIfNull(dateTime);
        ConnectionQueryConfig config = Utils.createConfig(maxWalkingDuration, maxTransferNumber, maxTravelTime,
                minTransferTime);

        // TODO: Same source and target stop throws exception, introduce specific exceptions for this and out of date range
        if (sourceStop != null && targetStop != null) {
            return map(service.getConnections(sourceStop, targetStop, dateTime, map(timeType), config));
        } else if (sourceStop != null) {
            return map(service.getConnections(sourceStop, targetCoordinate, dateTime, map(timeType), config));
        } else if (targetStop != null) {
            return map(service.getConnections(sourceCoordinate, targetStop, dateTime, map(timeType), config));
        } else {
            return map(service.getConnections(sourceCoordinate, targetCoordinate, dateTime, map(timeType), config));
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
                                            @RequestParam(required = false, defaultValue = "2147483647") int maxWalkingDuration,
                                            @RequestParam(required = false, defaultValue = "2147483647") int maxTransferNumber,
                                            @RequestParam(required = false, defaultValue = "2147483647") int maxTravelTime,
                                            @RequestParam(required = false, defaultValue = "0") int minTransferTime,
                                            @RequestParam(required = false, defaultValue = "false") boolean returnConnections) {

        GeoCoordinate sourceCoordinate = Utils.getCoordinateIfAvailable(sourceStopId, sourceLatitude, sourceLongitude,
                GlobalStopType.SOURCE);
        Stop sourceStop = Utils.getStopIfAvailable(sourceStopId, service, GlobalStopType.SOURCE);
        dateTime = Utils.setToNowIfNull(dateTime);
        ConnectionQueryConfig config = Utils.createConfig(maxWalkingDuration, maxTransferNumber, maxTravelTime,
                minTransferTime);

        if (sourceStop != null) {
            return map(service.getIsoLines(sourceStop, dateTime, map(timeType), config), timeType, returnConnections);
        } else {
            return map(service.getIsoLines(sourceCoordinate, dateTime, map(timeType), config), timeType,
                    returnConnections);
        }
    }

    private static class Utils {

        private static @Nullable GeoCoordinate getCoordinateIfAvailable(@Nullable String stopId,
                                                                        @Nullable Double latitude,
                                                                        @Nullable Double longitude,
                                                                        GlobalStopType stopType) {
            RoutingRequestValidator.validateStopParameters(stopId, latitude, longitude, stopType);
            return stopId == null ? RoutingRequestValidator.validateCoordinate(latitude, longitude) : null;
        }

        private static @Nullable Stop getStopIfAvailable(@Nullable String stopId, ScheduleInformationService service,
                                                         GlobalStopType stopType) {
            return stopId != null ? GlobalStopValidator.validateAndGetStop(stopId, service, stopType) : null;
        }

        private static LocalDateTime setToNowIfNull(LocalDateTime dateTime) {
            return (dateTime == null) ? LocalDateTime.now() : dateTime;
        }

        private static ConnectionQueryConfig createConfig(int maxWalkingDuration, int maxTransferNumber,
                                                          int maxTravelTime, int minTransferTime) {
            RoutingRequestValidator.validateQueryParams(maxWalkingDuration, maxTransferNumber, maxTravelTime,
                    minTransferTime);
            return new ConnectionQueryConfig(maxWalkingDuration, minTransferTime, maxTransferNumber, maxTravelTime);
        }

    }
}



