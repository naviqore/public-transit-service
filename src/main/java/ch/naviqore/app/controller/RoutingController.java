package ch.naviqore.app.controller;

import ch.naviqore.app.dto.Connection;
import ch.naviqore.app.dto.DtoDummyData;
import ch.naviqore.app.dto.DtoMapper;
import ch.naviqore.app.dto.EarliestArrival;
import ch.naviqore.service.PublicTransitService;
import ch.naviqore.service.Stop;
import ch.naviqore.service.TimeType;
import ch.naviqore.service.config.ConnectionQueryConfig;
import ch.naviqore.service.exception.StopNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

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
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                    "Location based routing is not implemented yet.");
        }

        if (targetStopId == null) {
            if (targetLatitude < 0 || targetLongitude < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Either targetStopId or targetLatitude and targetLongitude must be provided.");
            }
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                    "Location based routing is not implemented yet.");
        }

        if (departureDateTime == null) {
            departureDateTime = LocalDateTime.now();
        }

        Stop sourceStop = getStop(sourceStopId);
        Stop targetStop = getStop(targetStopId);
        ConnectionQueryConfig config = new ConnectionQueryConfig(maxWalkingDuration, minTransferTime, maxTransferNumber,
                maxTravelTime);

        return service.getConnections(sourceStop, targetStop, departureDateTime, TimeType.DEPARTURE, config)
                .stream()
                .map(DtoMapper::map)
                .toList();
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

        // TODO: Implement the method with maxWalkingDuration, maxTransferNumber, minTransferTime
        return DtoDummyData.getIsolines(sourceStopId, departureDateTime, maxTravelTime);
        // TODO: Decide on location or stop id.
        // service.getIsolines();
    }

    private ch.naviqore.service.Stop getStop(String stopId) {
        try {
            return service.getStopById(stopId);
        } catch (StopNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stop not found", e);
        }
    }

}
