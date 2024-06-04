package ch.naviqore.app.controller;

import ch.naviqore.app.dto.Connection;
import ch.naviqore.app.dto.DtoMapper;
import ch.naviqore.app.dto.EarliestArrival;
import ch.naviqore.service.PublicTransitService;
import ch.naviqore.service.Stop;
import ch.naviqore.service.TimeType;
import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.service.exception.StopNotFoundException;
import ch.naviqore.utils.spatial.GeoCoordinate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
                                           @RequestParam(required = false, defaultValue = "-1.0") double sourceLatitude,
                                           @RequestParam(required = false, defaultValue = "-1.0") double sourceLongitude,
                                           @RequestParam(required = false) String targetStopId,
                                           @RequestParam(required = false, defaultValue = "-1.0") double targetLatitude,
                                           @RequestParam(required = false, defaultValue = "-1.0") double targetLongitude,
                                           @RequestParam(required = false) LocalDateTime departureDateTime,
                                           @RequestParam(required = false, defaultValue = "2147483647") int maxWalkingDuration,
                                           @RequestParam(required = false, defaultValue = "2147483647") int maxTransferNumber,
                                           @RequestParam(required = false, defaultValue = "2147483647") int maxTravelTime,
                                           @RequestParam(required = false, defaultValue = "0") int minTransferTime) {
        if (sourceStopId == null) {
            if (sourceLatitude < 0 || sourceLongitude < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Either sourceStopId or sourceLatitude and sourceLongitude must be provided.");
            }
        }

        if (targetStopId == null) {
            if (targetLatitude < 0 || targetLongitude < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Either targetStopId or targetLatitude and targetLongitude must be provided.");
            }
        }

        if (departureDateTime == null) {
            departureDateTime = LocalDateTime.now();
        }

        Stop sourceStop = sourceStopId != null ? getStop(sourceStopId) : null;
        Stop targetStop = targetStopId != null ? getStop(targetStopId) : null;

        GeoCoordinate sourceCoordinate = sourceStop == null ? new GeoCoordinate(sourceLatitude, sourceLongitude) : null;
        GeoCoordinate targetCoordinate = targetStop == null ? new GeoCoordinate(targetLatitude, targetLongitude) : null;

        ConnectionQueryConfig config = new ConnectionQueryConfig(maxWalkingDuration, minTransferTime, maxTransferNumber,
                maxTravelTime);

        List<ch.naviqore.service.Connection> connections;

        if (sourceStop != null && targetStop != null) {
            connections = service.getConnections(sourceStop, targetStop, departureDateTime, TimeType.DEPARTURE, config);
        } else if (sourceStop != null) {
            connections = service.getConnections(sourceStop, targetCoordinate, departureDateTime, TimeType.DEPARTURE,
                    config);
        } else if (targetStop != null) {
            connections = service.getConnections(sourceCoordinate, targetStop, departureDateTime, TimeType.DEPARTURE,
                    config);
        } else {
            connections = service.getConnections(sourceCoordinate, targetCoordinate, departureDateTime,
                    TimeType.DEPARTURE, config);
        }

        return connections.stream().map(DtoMapper::map).toList();
    }

    @GetMapping("/isolines")
    public List<EarliestArrival> getIsolines(@RequestParam(required = false) String sourceStopId,
                                             @RequestParam(required = false, defaultValue = "-1.0") double sourceLatitude,
                                             @RequestParam(required = false, defaultValue = "-1.0") double sourceLongitude,
                                             @RequestParam(required = false) LocalDateTime departureDateTime,
                                             @RequestParam(required = false, defaultValue = "2147483647") int maxWalkingDuration,
                                             @RequestParam(required = false, defaultValue = "2147483647") int maxTransferNumber,
                                             @RequestParam(required = false, defaultValue = "2147483647") int maxTravelTime,
                                             @RequestParam(required = false, defaultValue = "0") int minTransferTime) {
        if (sourceStopId == null) {
            if (sourceLatitude < 0 || sourceLongitude < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Either sourceStopId or sourceLatitude and sourceLongitude must be provided.");
            }
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                    "Location based routing is not implemented yet.");
        }

        Stop sourceStop = getStop(sourceStopId);
        ConnectionQueryConfig config = new ConnectionQueryConfig(maxWalkingDuration, minTransferTime, maxTransferNumber,
                maxTravelTime);

        Map<Stop, ch.naviqore.service.Connection> connections = service.getIsolines(sourceStop, departureDateTime,
                config);

        List<EarliestArrival> arrivals = new ArrayList<>();

        for (Map.Entry<Stop, ch.naviqore.service.Connection> entry : connections.entrySet()) {
            Stop stop = entry.getKey();
            ch.naviqore.service.Connection connection = entry.getValue();
            arrivals.add(map(stop, connection));
        }

        return arrivals;
    }

    private ch.naviqore.service.Stop getStop(String stopId) {
        try {
            return service.getStopById(stopId);
        } catch (StopNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stop not found", e);
        }
    }

}
