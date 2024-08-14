package ch.naviqore.app.controller;

import ch.naviqore.app.dto.*;
import ch.naviqore.service.ScheduleInformationService;
import ch.naviqore.utils.spatial.GeoCoordinate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

import static ch.naviqore.app.dto.DtoMapper.map;

@RestController
@RequestMapping("/schedule")
@Tag(name = "schedule", description = "APIs related to scheduling and stops")
@Slf4j
public class ScheduleController {

    private final ScheduleInformationService service;

    @Autowired
    public ScheduleController(ScheduleInformationService service) {
        this.service = service;
    }

    @Operation(summary = "Autocomplete stop names", description = "Provides stop names and their corresponding stop IDs based on a partial input query.")
    @ApiResponse(responseCode = "200", description = "A list of stop names and IDs that match the query", content = @Content(schema = @Schema(implementation = Stop.class, type = "array")))
    @ApiResponse(responseCode = "400", description = "Invalid input parameters", content = @Content(schema = @Schema()))
    @GetMapping("/stops/autocomplete")
    public List<Stop> getAutoCompleteStops(@RequestParam String query,
                                           @RequestParam(required = false, defaultValue = "10") int limit,
                                           @RequestParam(required = false, defaultValue = "STARTS_WITH") SearchType searchType) {
        ScheduleRequestValidator.validateLimit(limit);
        return service.getStops(query, map(searchType)).stream().map(DtoMapper::map).limit(limit).toList();
    }

    @Operation(summary = "Get nearest stops", description = "Retrieves a list of stops within a specified distance from a given location.")
    @ApiResponse(responseCode = "200", description = "A list of nearest stops", content = @Content(schema = @Schema(implementation = DistanceToStop.class, type = "array")))
    @ApiResponse(responseCode = "400", description = "Invalid input parameters", content = @Content(schema = @Schema()))
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

    @Operation(summary = "Get information about a stop", description = "Provides detailed information about a specific stop, including coordinates and the name.")
    @ApiResponse(responseCode = "200", description = "Information about the specified stop.", content = @Content(schema = @Schema(implementation = Stop.class)))
    @ApiResponse(responseCode = "404", description = "StopID does not exist", content = @Content(schema = @Schema()))
    @GetMapping("/stops/{stopId}")
    public Stop getStop(@PathVariable String stopId) {
        return map(GlobalStopValidator.validateAndGetStop(stopId, service, GlobalStopType.NOT_DEFINED));
    }

    @Operation(summary = "Get next departures from a stop", description = "Retrieves the next departures from a specified stop at a given datetime.")
    @ApiResponse(responseCode = "200", description = "A list of the next departures from the specified stop.", content = @Content(schema = @Schema(implementation = Departure.class, type = "array")))
    @ApiResponse(responseCode = "400", description = "Invalid input parameters", content = @Content(schema = @Schema()))
    @ApiResponse(responseCode = "404", description = "StopID does not exist", content = @Content(schema = @Schema()))
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
