package ch.naviqore.app.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@RequiredArgsConstructor
@ToString
@Getter
public class ScheduleInfo {
    final boolean hasAccessibility;
    final boolean hasBikes;
    final boolean hasTravelModes;
    final ScheduleValidity scheduleValidity;
}
