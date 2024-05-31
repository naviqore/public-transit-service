package ch.naviqore.app.controller;

import ch.naviqore.app.dto.*;
import ch.naviqore.service.Location;
import ch.naviqore.service.ScheduleInformationService;
import ch.naviqore.service.exception.StopNotFoundException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static ch.naviqore.app.dto.DtoMapper.map;

@RestController
@RequestMapping("/schedule")
@Log4j2
public class ScheduleController {

    private final ScheduleInformationService service;

    @Autowired
    public ScheduleController(ScheduleInformationService service) {
        this.service = service;
    }

    @GetMapping("/stops/autocomplete")
    public List<Stop> getAutoCompleteStops(@RequestParam String query,
                                           @RequestParam(required = false, defaultValue = "10") int limit,
                                           @RequestParam(required = false, defaultValue = "STARTS_WITH") SearchType type) {
        return service.getStops(query, map(type)).stream().map(DtoMapper::map).limit(limit).toList();
    }

    @GetMapping("/stops/nearest")
    public List<DistanceToStop> getNearestStops(@RequestParam double latitude, @RequestParam double longitude,
                                                @RequestParam(required = false, defaultValue = "1000") int maxDistance,
                                                @RequestParam(required = false, defaultValue = "10") int limit) {
        return service.getNearestStops(new Location(latitude, longitude), maxDistance, limit)
                .stream()
                .map(stop -> map(stop, latitude, longitude))
                .toList();
    }

    @GetMapping("/stops/{stopId}")
    public Stop getStop(@PathVariable String stopId) {
        try {
            return map(service.getStopById(stopId));
        } catch (StopNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stop not found", e);
        }
    }

    @GetMapping("/stops/{stopId}/departures")
    public List<Departure> getDepartures(@PathVariable String stopId,
                                         @RequestParam(required = false) LocalDateTime departureDateTime,
                                         @RequestParam(required = false, defaultValue = "10") int limit,
                                         @RequestParam(required = false) LocalDateTime untilDateTime) {
        if (departureDateTime == null) {
            departureDateTime = LocalDateTime.now();
        }

        try {
            return service.getNextDepartures(service.getStopById(stopId), departureDateTime, untilDateTime, limit)
                    .stream()
                    .map(DtoMapper::map)
                    .toList();
        } catch (StopNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stop not found", e);
        }

    }

}
