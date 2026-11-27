package org.naviqore.app.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@RequiredArgsConstructor
@ToString
@Getter
@Schema(description = "What the schedule tells, and the dates it covers.")
public class ScheduleInfo {

    @Schema(description = "Whether the schedule tells which trips are accessible by wheelchair; if not, wheelchairAccessible of each trip is false.")
    final boolean hasWheelchairAccessible;

    @Schema(description = "Whether the schedule tells which trips allow bikes; if not, bikeAllowed of each trip is false.")
    final boolean hasBikeAllowed;

    @Schema(description = "Whether the schedule tells the travel mode of each route.")
    final boolean hasTravelModes;

    @Schema(description = "The dates the schedule covers.")
    final ScheduleValidity scheduleValidity;

}
