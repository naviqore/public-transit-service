package ch.naviqore.app.controller;

import ch.naviqore.app.dto.*;
import ch.naviqore.service.ScheduleInformationService;
import ch.naviqore.utils.spatial.GeoCoordinate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/stops/autocomplete")
    public List<Stop> getAutoCompleteStops(@RequestParam String query,
                                           @RequestParam(required = false, defaultValue = "10") int limit,
                                           @RequestParam(required = false, defaultValue = "STARTS_WITH") SearchType searchType) {
        ScheduleRequestValidator.validateLimit(limit);
        return service.getStops(query, map(searchType)).stream().map(DtoMapper::map).limit(limit).toList();
    }

    @GetMapping("/stops/nearest")
    public List<DistanceToStop> getNearestStops(@RequestParam double latitude, @RequestParam double longitude,
                                                @RequestParam(required = false, defaultValue = "1000") int maxDistance,
                                                @RequestParam(required = false, defaultValue = "10") int limit) {
        ScheduleRequestValidator.validateLimit(limit);
        ScheduleRequestValidator.validateMaxDistance(maxDistance);

        GeoCoordinate location = ScheduleRequestValidator.validateGeoCoordinate(latitude, longitude);

        return service.getNearestStops(location, maxDistance, limit)
                .stream()
                .map(stop -> map(stop, latitude, longitude))
                .toList();
    }

    @GetMapping("/stops/{stopId}")
    public Stop getStop(@PathVariable String stopId) {
        return map(GlobalStopValidator.validateAndGetStop(stopId, service, GlobalStopType.NOT_DEFINED));
    }

    @GetMapping("/stops/{stopId}/departures")
    public List<Departure> getDepartures(@PathVariable String stopId,
                                         @RequestParam(required = false) LocalDateTime departureDateTime,
                                         @RequestParam(required = false, defaultValue = "10") int limit,
                                         @RequestParam(required = false) LocalDateTime untilDateTime) {
        ScheduleRequestValidator.validateLimit(limit);
        departureDateTime = ScheduleRequestValidator.validateAndSetDefaultDateTime(departureDateTime);
        ScheduleRequestValidator.validateUntilDateTime(departureDateTime, untilDateTime);

        return service.getNextDepartures(
                GlobalStopValidator.validateAndGetStop(stopId, service, GlobalStopType.NOT_DEFINED), departureDateTime,
                untilDateTime, limit).stream().map(DtoMapper::map).toList();
    }

}
