package org.naviqore.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;
import org.naviqore.app.dto.*;
import org.naviqore.app.service.ValidationService;
import org.naviqore.service.ScheduleInformationService;
import org.naviqore.utils.spatial.GeoCoordinate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

import static org.naviqore.app.dto.DtoMapper.map;

@RestController
@RequestMapping("/schedule")
@Tag(name = "schedule", description = "APIs related to scheduling and stops")
@Slf4j
@Validated
public class ScheduleController {

    private final ScheduleInformationService service;

    @Autowired
    public ScheduleController(ScheduleInformationService service) {
        this.service = service;
    }

    @Operation(summary = "Get information about the schedule", description = "Get all relevant information about the schedule, such as supported features and validity.")
    @ApiResponse(responseCode = "200", description = "A list of details supported or not supported by the schedule and it's validity.")
    @GetMapping("")
    public ScheduleInfo getScheduleInfo() {
        return new ScheduleInfo(service.hasAccessibilityInformation(), service.hasBikeInformation(),
                service.hasTravelModeInformation(), map(service.getValidity()));
    }

    @Operation(summary = "Autocomplete stop names", description = "Provides stop names and their corresponding stop IDs based on a partial input query.")
    @ApiResponse(responseCode = "200", description = "A list of stop names and IDs that match the query", content = @Content(schema = @Schema(implementation = Stop.class, type = "array")))
    @ApiResponse(responseCode = "400", description = "Invalid input parameters", content = @Content(schema = @Schema()))
    @GetMapping("/stops/autocomplete")
    public List<Stop> getAutoCompleteStops(@RequestParam String query,
                                           @RequestParam(required = false, defaultValue = "10") @Min(1) int limit,
                                           @RequestParam(required = false, defaultValue = "CONTAINS") SearchType searchType,
                                           @RequestParam(required = false, defaultValue = "RELEVANCE") StopSortStrategy stopSortStrategy) {
        return service.getStops(query, map(searchType), map(stopSortStrategy))
                .stream()
                .map(DtoMapper::map)
                .limit(limit)
                .toList();
    }

    @Operation(summary = "Get nearest stops", description = "Retrieves a list of stops within a specified distance from a given location.")
    @ApiResponse(responseCode = "200", description = "A list of nearest stops", content = @Content(schema = @Schema(implementation = DistanceToStop.class, type = "array")))
    @ApiResponse(responseCode = "400", description = "Invalid input parameters", content = @Content(schema = @Schema()))
    @GetMapping("/stops/nearest")
    public List<DistanceToStop> getNearestStops(@RequestParam double latitude, @RequestParam double longitude,
                                                @RequestParam(required = false, defaultValue = "1000") @Min(0) int maxDistance,
                                                @RequestParam(required = false, defaultValue = "10") @Min(1) int limit) {
        GeoCoordinate location = ValidationService.validateAndCreateCoordinate(latitude, longitude);
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
        return map(Utils.getStopById(stopId, service));
    }

    @Operation(summary = "Get next departures from a stop", description = "Retrieves the next departures from a specified stop at a given datetime.")
    @ApiResponse(responseCode = "200", description = "A list of the next departures from the specified stop.", content = @Content(schema = @Schema(implementation = Departure.class, type = "array")))
    @ApiResponse(responseCode = "400", description = "Invalid input parameters", content = @Content(schema = @Schema()))
    @ApiResponse(responseCode = "404", description = "StopID does not exist", content = @Content(schema = @Schema()))
    @GetMapping("/stops/{stopId}/departures")
    public List<Departure> getDepartures(@PathVariable String stopId,
                                         @RequestParam(required = false) LocalDateTime departureDateTime,
                                         @RequestParam(required = false, defaultValue = "10") @Min(1) int limit,
                                         @RequestParam(required = false) LocalDateTime untilDateTime) {
        departureDateTime = ValidationService.validateAndSetDefaultDateTime(departureDateTime, service);
        ValidationService.validateUntilDateTime(departureDateTime, untilDateTime);
        return service.getNextDepartures(Utils.getStopById(stopId, service), departureDateTime, untilDateTime, limit)
                .stream()
                .map(DtoMapper::map)
                .toList();
    }

    private static class Utils {
        private static org.naviqore.service.Stop getStopById(String stopId, ScheduleInformationService service) {
            try {
                return service.getStopById(stopId);
            } catch (org.naviqore.service.exception.StopNotFoundException e) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        String.format("The requested stop with ID '%s' was not found.", stopId), e);
            }
        }
    }
}
