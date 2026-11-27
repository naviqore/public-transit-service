package org.naviqore.app.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@RequiredArgsConstructor
@ToString
@Getter
public class ScheduleInfo {

    final boolean hasWheelchairAccessible;
    final boolean hasBikeAllowed;
    final boolean hasTravelModes;
    final ScheduleValidity scheduleValidity;

}
