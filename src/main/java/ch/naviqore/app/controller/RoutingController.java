package ch.naviqore.app.controller;

import ch.naviqore.app.dto.Connection;
import ch.naviqore.app.dto.DtoMapper;
import ch.naviqore.app.dto.StopConnection;
import ch.naviqore.app.dto.TimeType;
import ch.naviqore.service.PublicTransitService;
import ch.naviqore.service.Stop;
import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.utils.spatial.GeoCoordinate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

        RoutingRequestValidator.validateStopParameters(sourceStopId, sourceLatitude, sourceLongitude, "source");
        RoutingRequestValidator.validateStopParameters(targetStopId, targetLatitude, targetLongitude, "target");

        GeoCoordinate sourceCoordinate = sourceStopId == null ? RoutingRequestValidator.validateCoordinate(
                sourceLatitude, sourceLongitude) : null;

        GeoCoordinate targetCoordinate = targetStopId == null ? RoutingRequestValidator.validateCoordinate(
                targetLatitude, targetLongitude) : null;

        dateTime = RoutingRequestValidator.setToNowIfNull(dateTime);

        Stop sourceStop = sourceStopId != null ? GlobalStopValidator.validateAndGetStop(sourceStopId, service,
                GlobalStopValidator.StopType.SOURCE) : null;
        Stop targetStop = targetStopId != null ? GlobalStopValidator.validateAndGetStop(targetStopId, service,
                GlobalStopValidator.StopType.TARGET) : null;

        RoutingRequestValidator.validateQueryParams(maxWalkingDuration, maxTransferNumber, maxTravelTime,
                minTransferTime);
        ConnectionQueryConfig config = new ConnectionQueryConfig(maxWalkingDuration, minTransferTime, maxTransferNumber,
                maxTravelTime);

        List<ch.naviqore.service.Connection> connections;

        if (sourceStop != null && targetStop != null) {
            connections = service.getConnections(sourceStop, targetStop, dateTime, map(timeType), config);
        } else if (sourceStop != null) {
            connections = service.getConnections(sourceStop, targetCoordinate, dateTime, map(timeType), config);
        } else if (targetStop != null) {
            connections = service.getConnections(sourceCoordinate, targetStop, dateTime, map(timeType), config);
        } else {
            connections = service.getConnections(sourceCoordinate, targetCoordinate, dateTime, map(timeType), config);
        }

        return connections.stream().map(DtoMapper::map).toList();
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

        RoutingRequestValidator.validateStopParameters(sourceStopId, sourceLatitude, sourceLongitude, "source");

        GeoCoordinate sourceCoordinate = sourceStopId == null ? RoutingRequestValidator.validateCoordinate(
                sourceLatitude, sourceLongitude) : null;

        Stop sourceStop = sourceStopId != null ? GlobalStopValidator.validateAndGetStop(sourceStopId, service,
                GlobalStopValidator.StopType.SOURCE) : null;

        RoutingRequestValidator.validateQueryParams(maxWalkingDuration, maxTransferNumber, maxTravelTime,
                minTransferTime);
        ConnectionQueryConfig config = new ConnectionQueryConfig(maxWalkingDuration, minTransferTime, maxTransferNumber,
                maxTravelTime);

        dateTime = RoutingRequestValidator.setToNowIfNull(dateTime);

        Map<Stop, ch.naviqore.service.Connection> connections;
        if (sourceStop != null) {
            connections = service.getIsoLines(sourceStop, dateTime, map(timeType), config);
        } else {
            connections = service.getIsoLines(sourceCoordinate, dateTime, map(timeType), config);
        }

        List<StopConnection> arrivals = new ArrayList<>();

        for (Map.Entry<Stop, ch.naviqore.service.Connection> entry : connections.entrySet()) {
            Stop stop = entry.getKey();
            ch.naviqore.service.Connection connection = entry.getValue();
            arrivals.add(new StopConnection(stop, connection, timeType, returnConnections));
        }

        return arrivals;
    }
}
