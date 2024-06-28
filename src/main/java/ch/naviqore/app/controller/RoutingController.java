package ch.naviqore.app.controller;

import ch.naviqore.app.dto.Connection;
import ch.naviqore.app.dto.DtoMapper;
import ch.naviqore.app.dto.StopConnection;
import ch.naviqore.app.dto.TimeType;
import ch.naviqore.service.PublicTransitService;
import ch.naviqore.service.Stop;
import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.service.exception.StopNotFoundException;
import ch.naviqore.utils.spatial.GeoCoordinate;
import org.jetbrains.annotations.NotNull;
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

    private static GeoCoordinate validateCoordinate(double latitude, double longitude) {
        try {
            return new GeoCoordinate(latitude, longitude);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Coordinates must be valid, Latitude between -90 and 90 and Longitude between -180 and 180.");
        }
    }

    private static void validateQueryParams(int maxWalkingDuration, int maxTransferNumber, int maxTravelTime,
                                            int minTransferTime) {
        if (maxWalkingDuration < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Max walking duration must be greater than or equal to 0.");
        }
        if (maxTransferNumber < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Max transfer number must be greater than or equal to 0.");
        }
        if (maxTravelTime <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Max travel time must be greater than 0.");
        }
        if (minTransferTime < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Min transfer time must be greater than or equal to 0.");
        }
    }

    private static @NotNull LocalDateTime setToNowIfNull(LocalDateTime dateTime) {
        if (dateTime == null) {
            dateTime = LocalDateTime.now();
        }
        return dateTime;
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

        GeoCoordinate sourceCoordinate = null;
        GeoCoordinate targetCoordinate = null;

        if (sourceStopId == null) {
            if (sourceLatitude == -91.0 || sourceLongitude == -181.0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Either sourceStopId or sourceLatitude and sourceLongitude must be provided.");
            }
            sourceCoordinate = validateCoordinate(sourceLatitude, sourceLongitude);
        }

        if (targetStopId == null) {
            if (targetLatitude == -91.0 || targetLongitude == -181.0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Either targetStopId or targetLatitude and targetLongitude must be provided.");
            }
            targetCoordinate = validateCoordinate(targetLatitude, targetLongitude);
        }

        dateTime = setToNowIfNull(dateTime);

        Stop sourceStop = sourceStopId != null ? getStop(sourceStopId) : null;
        Stop targetStop = targetStopId != null ? getStop(targetStopId) : null;

        validateQueryParams(maxWalkingDuration, maxTransferNumber, maxTravelTime, minTransferTime);
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

        GeoCoordinate sourceCoordinate = null;
        if (sourceStopId == null) {
            if (sourceLatitude == -91.0 || sourceLongitude == -181.0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Either sourceStopId or sourceLatitude and sourceLongitude must be provided.");
            }
            sourceCoordinate = validateCoordinate(sourceLatitude, sourceLongitude);
        }

        Stop sourceStop = sourceStopId != null ? getStop(sourceStopId) : null;

        validateQueryParams(maxWalkingDuration, maxTransferNumber, maxTravelTime, minTransferTime);
        ConnectionQueryConfig config = new ConnectionQueryConfig(maxWalkingDuration, minTransferTime, maxTransferNumber,
                maxTravelTime);

        dateTime = setToNowIfNull(dateTime);

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
            arrivals.add(map(stop, connection, map(timeType), returnConnections));
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
