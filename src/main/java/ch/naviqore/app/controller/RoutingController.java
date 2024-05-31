package ch.naviqore.app.controller;

import ch.naviqore.app.dto.Connection;
import ch.naviqore.app.dto.DtoDummyData;
import ch.naviqore.app.dto.EarliestArrival;
import ch.naviqore.service.ConnectionRoutingService;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/routing")
public class RoutingController {

    private final ConnectionRoutingService service;

    @Autowired
    public RoutingController(ConnectionRoutingService service) {
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
        if( sourceStopId == null ) {
            if( sourceLatitude < 0 || sourceLongitude < 0 ) {
                throw new IllegalArgumentException("Either sourceStopId or sourceLatitude and sourceLongitude must be provided.");
            }
            throw new NotImplementedException("Location based routing is not implemented yet.");
        }

        if (targetStopId == null) {
            if (targetLatitude < 0 || targetLongitude < 0) {
                throw new IllegalArgumentException("Either targetStopId or targetLatitude and targetLongitude must be provided.");
            }
            throw new NotImplementedException("Location based routing is not implemented yet.");
        }

        // TODO: Implement the method with  maxWalkingDuration, maxTransferNumber, maxTravelTime, minTransferTime
        return DtoDummyData.getConnections(sourceStopId, targetStopId, departureDateTime);
        // TODO: Decide on location or stop id.
        // service.getConnections()
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
        if( sourceStopId == null ) {
            if( sourceLatitude < 0 || sourceLongitude < 0 ) {
                throw new IllegalArgumentException("Either sourceStopId or sourceLatitude and sourceLongitude must be provided.");
            }
            throw new NotImplementedException("Location based routing is not implemented yet.");
        }

        // TODO: Implement the method with maxWalkingDuration, maxTransferNumber, minTransferTime
        return DtoDummyData.getIsolines(sourceStopId, departureDateTime, maxTravelTime);
        // TODO: Decide on location or stop id.
        // service.getIsolines();
    }

}
