package org.naviqore.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.naviqore.app.dto.*;
import org.naviqore.service.ScheduleInformationService;
import org.naviqore.utils.spatial.GeoCoordinate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.naviqore.app.dto.DtoMapper.map;

@RestController
@RequestMapping("/schedule")
@Tag(name = "schedule", description = "APIs related to scheduling and stops")
@Slf4j
@Validated
@RequiredArgsConstructor
public class ScheduleController {

    // autocomplete and nearest stops returns parents or stops without a parent, children scope fits most use cases
    public static final String DEFAULT_STOP_SCOPE = "CHILDREN";
    private static final Duration DEFAULT_WINDOW = Duration.ofHours(6);
    private static final String DEFAULT_LIMIT = "10";
    private static final String DEFAULT_MAX_DISTANCE = "1000";
    private static final String DEFAULT_SEARCH_TYPE = "CONTAINS";
    private static final String DEFAULT_SORT_BY = "RELEVANCE";
    private static final String DEFAULT_TIME_TYPE = "DEPARTURE";
    private final ScheduleInformationService service;

    @Operation(summary = "Get information about the schedule", description = "Get all relevant information about the schedule, such as supported features and validity.")
    @ApiResponse(responseCode = "200", description = "A list of details supported or not supported by the schedule and it's validity.")
    @GetMapping("")
    public ScheduleInfo getScheduleInfo() {
        return new ScheduleInfo(service.hasWheelchairAccessible(), service.hasBikeAllowed(), service.hasTravelModes(),
                map(service.getValidity()));
    }

    @Operation(summary = "Autocomplete stop names", description = "Provides stop names and their corresponding stop IDs based on a partial input query.")
    @ApiResponse(responseCode = "200", description = "A list of stop names and IDs that match the query", content = @Content(schema = @Schema(implementation = Stop.class, type = "array")))
    @ApiResponse(responseCode = "400", description = "Invalid input parameters")
    @GetMapping("/stops/autocomplete")
    public List<Stop> getAutoCompleteStops(@RequestParam @Parameter(description = "Text to search the names of the stops for, ignoring case. A stop of a station is found by the name of its station.") String query,
                                           @RequestParam(defaultValue = DEFAULT_SEARCH_TYPE) @Parameter(description = "Where the text has to be in the name.") SearchType searchType,
                                           @RequestParam(defaultValue = DEFAULT_SORT_BY) @Parameter(description = "Order of the stops: by RELEVANCE, the exact names first, then those starting with the text, the shorter first; or ALPHABETICAL.") StopSortStrategy sortBy,
                                           @RequestParam(defaultValue = DEFAULT_LIMIT) @Parameter(description = "Maximum number of stops.") @Min(1) int limit) {

        return service.getStops(query, map(searchType), map(sortBy)).stream().map(DtoMapper::map).limit(limit).toList();
    }

    @Operation(summary = "Get nearest stops", description = "Retrieves a list of stops within a specified distance from a given location.")
    @ApiResponse(responseCode = "200", description = "A list of nearest stops", content = @Content(schema = @Schema(implementation = DistanceToStop.class, type = "array")))
    @ApiResponse(responseCode = "400", description = "Invalid input parameters")
    @GetMapping("/stops/nearest")
    public List<DistanceToStop> getNearestStops(@RequestParam @Parameter(description = "Latitude of the location, in WGS84 degrees from -90 to 90.") double latitude,
                                                @RequestParam @Parameter(description = "Longitude of the location, in WGS84 degrees from -180 to 180.") double longitude,
                                                @RequestParam(defaultValue = DEFAULT_MAX_DISTANCE) @Parameter(description = "Radius around the location to search for stops in, in meters.") @Min(0) int maxDistance,
                                                @RequestParam(defaultValue = DEFAULT_LIMIT) @Parameter(description = "Maximum number of stops.") @Min(1) int limit) {

        GeoCoordinate location = RequestValidator.createCoordinate(latitude, longitude);

        return service.getNearestStops(location, maxDistance)
                .stream()
                .limit(limit)
                .map(stop -> map(stop, latitude, longitude))
                .toList();
    }

    @Operation(summary = "Get a random stop", description = "Retrieves a random stop from the schedule.")
    @ApiResponse(responseCode = "200", description = "A random stop from the schedule.", content = @Content(schema = @Schema(implementation = Stop.class)))
    @GetMapping("/stops/random")
    public Stop getRandomStop() {
        return map(service.getRandomStop());
    }

    @Operation(summary = "Get information about a stop", description = "Provides detailed information about a specific stop, including coordinates and the name.")
    @ApiResponse(responseCode = "200", description = "Information about the specified stop.", content = @Content(schema = @Schema(implementation = Stop.class)))
    @ApiResponse(responseCode = "404", description = "Stop not found")
    @GetMapping("/stops/{stopId}")
    public Stop getStop(@PathVariable @Parameter(description = "ID of the stop.") String stopId) {
        return map(RequestValidator.getStopById(stopId, service));
    }

    @Operation(summary = "Get stop times (departures or arrivals) for a stop", description = "Retrieves the next stop times from a specified stop at a given datetime.")
    @ApiResponse(responseCode = "200", description = "A list of the next stop times from the specified stop.", content = @Content(schema = @Schema(implementation = StopEvent.class, type = "array")))
    @ApiResponse(responseCode = "400", description = "Invalid input parameters")
    @ApiResponse(responseCode = "404", description = "Stop not found")
    @GetMapping("/stops/{stopId}/times")
    public List<StopEvent> getStopTimes(@PathVariable @Parameter(description = "ID of the stop.") String stopId,
                                        @RequestParam(required = false) @Parameter(description = "Start of the time window, included, as an ISO-8601 date-time with offset, e.g. 2026-03-01T08:00:00+01:00 (+ as %2B in a URL). Now if omitted; its date must lie within the validity of the schedule.") OffsetDateTime from,
                                        @RequestParam(required = false) @Parameter(description = "End of the time window, excluded, as an ISO-8601 date-time with offset. 6 hours after from if omitted; must not be before from.") OffsetDateTime to,
                                        @RequestParam(defaultValue = DEFAULT_TIME_TYPE) @Parameter(description = "Whether to list the departures (DEPARTURE) or the arrivals (ARRIVAL) in the time window.") TimeType timeType,
                                        @RequestParam(defaultValue = DEFAULT_STOP_SCOPE) @Parameter(description = "The stops to list the times of: the stop alone (STRICT), with its children (CHILDREN), its whole station (RELATED), or all stops within the walk search radius (NEARBY).") StopScope stopScope,
                                        @RequestParam(defaultValue = DEFAULT_LIMIT) @Parameter(description = "Maximum number of stop times, the earliest first.") @Min(1) int limit) {
        OffsetDateTime effectiveFrom = RequestValidator.validateAndSetDefaultDateTime(from, service);
        OffsetDateTime effectiveTo = Optional.ofNullable(to).orElseGet(() -> effectiveFrom.plus(DEFAULT_WINDOW));
        RequestValidator.validateTimeWindow(effectiveFrom, effectiveTo);

        return service.getStopTimes(RequestValidator.getStopById(stopId, service), effectiveFrom, effectiveTo,
                map(timeType), map(stopScope)).stream().limit(limit).map(DtoMapper::map).toList();
    }
}