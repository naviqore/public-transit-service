package ch.naviqore.app.controller;

import ch.naviqore.app.model.Departure;
import ch.naviqore.app.model.DistanceToStop;
import ch.naviqore.app.model.SearchType;
import ch.naviqore.app.model.Stop;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/schedule")
public class ScheduleController {

    @GetMapping("/stops/autocomplete")
    public List<Stop> getAutoCompleteStops(@RequestParam String query,
                                           @RequestParam(required = false, defaultValue = "10") int limit,
                                           @RequestParam(required = false, defaultValue = "FUZZY") SearchType type) {
        return DummyData.searchStops(query, limit, type);
    }

    @GetMapping("/stops/nearest")
    public List<DistanceToStop> getNearestStops(@RequestParam double latitude, @RequestParam double longitude,
                                                @RequestParam(required = false, defaultValue = "1000") int maxDistance,
                                                @RequestParam(required = false, defaultValue = "10") int limit) {
        return DummyData.getNearestStops(latitude, longitude, maxDistance, limit);
    }

    @GetMapping("/stops/{stopId}")
    public Stop getStop(@PathVariable String stopId) {
        return DummyData.getStop(stopId);
    }

    @GetMapping("/stops/{stopId}/departures")
    public List<Departure> getDepartures(@PathVariable String stopId,
                                         @RequestParam(required = false) LocalDateTime departureDateTime,
                                         @RequestParam(required = false, defaultValue = "10") int limit,
                                         @RequestParam(required = false) LocalDateTime untilDateTime) {
        return DummyData.getDepartures(stopId, departureDateTime, limit, untilDateTime);
    }

}
