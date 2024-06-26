package ch.naviqore.app.controller;

import ch.naviqore.app.dto.*;
import ch.naviqore.service.ScheduleInformationService;
import ch.naviqore.service.exception.StopNotFoundException;
import ch.naviqore.utils.spatial.GeoCoordinate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static ch.naviqore.app.dto.DtoMapper.map;

@RestController
@RequestMapping("/schedule")
@Slf4j
public class ScheduleController {

    private final ScheduleInformationService service;

    @Autowired
    public ScheduleController(ScheduleInformationService service) {
        this.service = service;
    }

    private static void validateLimit(int limit) {
        if (limit <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit must be greater than 0");
        }
    }

    @GetMapping("/stops/autocomplete")
    public List<Stop> getAutoCompleteStops(@RequestParam String query,
                                           @RequestParam(required = false, defaultValue = "10") int limit,
                                           @RequestParam(required = false, defaultValue = "STARTS_WITH") SearchType searchType) {
        validateLimit(limit);
        return service.getStops(query, map(searchType)).stream().map(DtoMapper::map).limit(limit).toList();
    }

    @GetMapping("/stops/nearest")
    public List<DistanceToStop> getNearestStops(@RequestParam double latitude, @RequestParam double longitude,
                                                @RequestParam(required = false, defaultValue = "1000") int maxDistance,
                                                @RequestParam(required = false, defaultValue = "10") int limit) {
        validateLimit(limit);
        if (maxDistance < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Max distance can not be negative");
        }

        GeoCoordinate location;
        try {
            location = new GeoCoordinate(latitude, longitude);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }

        return service.getNearestStops(location, maxDistance, limit)
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
        validateLimit(limit);
        if (departureDateTime == null) {
            departureDateTime = LocalDateTime.now();
        }
        if (untilDateTime != null) {
            if (untilDateTime.isBefore(departureDateTime)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Until date time must be after departure date time");
            }
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
