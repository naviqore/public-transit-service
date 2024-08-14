package ch.naviqore.app.controller;

import ch.naviqore.app.dto.Connection;
import ch.naviqore.app.dto.StopConnection;
import ch.naviqore.app.dto.TimeType;
import ch.naviqore.service.PublicTransitService;
import ch.naviqore.service.ScheduleInformationService;
import ch.naviqore.service.Stop;
import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.utils.spatial.GeoCoordinate;
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
public class RoutingController {

    private final PublicTransitService service;

    @Autowired
    public RoutingController(PublicTransitService service) {
        this.service = service;
    }

    @GetMapping("/connections")
    public List<Connection> getConnections(@RequestParam(required = false) String sourceStopId,
                                           @RequestParam(required = false, defaultValue = "-91.0") double sourceLatitude,
                                           @RequestParam(required = false, defaultValue = "-181.0") double sourceLongitude,
                                           @RequestParam(required = false) String targetStopId,
                                           @RequestParam(required = false, defaultValue = "-91.0") double targetLatitude,
                                           @RequestParam(required = false, defaultValue = "-181.0") double targetLongitude,
                                           @RequestParam(required = false) LocalDateTime dateTime,
                                           @RequestParam(required = false, defaultValue = "DEPARTURE") TimeType timeType,
                                           @RequestParam(required = false, defaultValue = "2147483647") int maxWalkingDuration,
                                           @RequestParam(required = false, defaultValue = "2147483647") int maxTransferNumber,
                                           @RequestParam(required = false, defaultValue = "2147483647") int maxTravelTime,
                                           @RequestParam(required = false, defaultValue = "0") int minTransferTime) {

        // try to get coordinates
        GeoCoordinate sourceCoordinate = Utils.getCoordinateIfAvailable(sourceStopId, sourceLatitude, sourceLongitude,
                GlobalStopType.SOURCE);
        GeoCoordinate targetCoordinate = Utils.getCoordinateIfAvailable(targetStopId, targetLatitude, targetLongitude,
                GlobalStopType.TARGET);

        // try to get stops by id
        Stop sourceStop = Utils.getStopIfAvailable(sourceStopId, service, GlobalStopType.SOURCE);
        Stop targetStop = Utils.getStopIfAvailable(targetStopId, service, GlobalStopType.TARGET);

        // configure connection request
        dateTime = Utils.setToNowIfNull(dateTime);
        ConnectionQueryConfig config = Utils.createConfig(maxWalkingDuration, maxTransferNumber, maxTravelTime,
                minTransferTime);

        // determine connection case: STOP_STOP, STOP_COORDINATE, COORDINATE_STOP or COORDINATE_COORDINATE
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

    @GetMapping("/isolines")
    public List<StopConnection> getIsolines(@RequestParam(required = false) String sourceStopId,
                                            @RequestParam(required = false, defaultValue = "-91") double sourceLatitude,
                                            @RequestParam(required = false, defaultValue = "-181") double sourceLongitude,
                                            @RequestParam(required = false) LocalDateTime dateTime,
                                            @RequestParam(required = false, defaultValue = "DEPARTURE") TimeType timeType,
                                            @RequestParam(required = false, defaultValue = "2147483647") int maxWalkingDuration,
                                            @RequestParam(required = false, defaultValue = "2147483647") int maxTransferNumber,
                                            @RequestParam(required = false, defaultValue = "2147483647") int maxTravelTime,
                                            @RequestParam(required = false, defaultValue = "0") int minTransferTime,
                                            @RequestParam(required = false, defaultValue = "false") boolean returnConnections) {

        // try to get coordinates or source stop
        GeoCoordinate sourceCoordinate = Utils.getCoordinateIfAvailable(sourceStopId, sourceLatitude, sourceLongitude,
                GlobalStopType.SOURCE);
        Stop sourceStop = Utils.getStopIfAvailable(sourceStopId, service, GlobalStopType.SOURCE);

        // configure isoline request
        dateTime = Utils.setToNowIfNull(dateTime);
        ConnectionQueryConfig config = Utils.createConfig(maxWalkingDuration, maxTransferNumber, maxTravelTime,
                minTransferTime);

        // determine isoline case: STOP or COORDINATE
        if (sourceStop != null) {
            return map(service.getIsoLines(sourceStop, dateTime, map(timeType), config), timeType, returnConnections);
        } else {
            return map(service.getIsoLines(sourceCoordinate, dateTime, map(timeType), config), timeType,
                    returnConnections);
        }

    }

    private static class Utils {

        private static @Nullable GeoCoordinate getCoordinateIfAvailable(String stopId, double latitude,
                                                                        double longitude, GlobalStopType stopType) {
            RoutingRequestValidator.validateStopParameters(stopId, latitude, longitude, stopType);
            return stopId == null ? RoutingRequestValidator.validateCoordinate(latitude, longitude) : null;
        }

        private static @Nullable Stop getStopIfAvailable(String stopId, ScheduleInformationService service,
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
