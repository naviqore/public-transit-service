package ch.naviqore.api.controller;

import ch.naviqore.api.model.Connection;
import ch.naviqore.api.model.EarliestArrival;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/routing")
public class RoutingController {

    @GetMapping("/connections")
    public List<Connection> getConnections(@RequestParam String sourceStopId, @RequestParam String targetStopId,
                                           @RequestParam(required = false) LocalDateTime departureDateTime,
                                           @RequestParam(required = false, defaultValue = "2147483647") int maxWalkingDuration,
                                           @RequestParam(required = false, defaultValue = "2147483647") int maxTransferNumber,
                                           @RequestParam(required = false, defaultValue = "2147483647") int maxTravelTime,
                                           @RequestParam(required = false, defaultValue = "0") int minTransferTime) {
        // TODO: Implement the method with  maxWalkingDuration, maxTransferNumber, maxTravelTime, minTransferTime
        return DummyData.getConnections(sourceStopId, targetStopId, departureDateTime);
    }

    @GetMapping("/isolines")
    public List<EarliestArrival> getIsolines(@RequestParam String sourceStopId,
                                             @RequestParam(required = false) LocalDateTime departureDateTime,
                                             @RequestParam(required = false, defaultValue = "2147483647") int maxWalkingDuration,
                                             @RequestParam(required = false, defaultValue = "2147483647") int maxTransferNumber,
                                             @RequestParam(required = false, defaultValue = "2147483647") int maxTravelTime,
                                             @RequestParam(required = false, defaultValue = "0") int minTransferTime) {
        // TODO: Implement the method with maxWalkingDuration, maxTransferNumber, minTransferTime
        return DummyData.getIsolines(sourceStopId, departureDateTime, maxTravelTime);
    }

}
