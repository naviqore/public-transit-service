package ch.naviqore.app.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

@RequiredArgsConstructor
@ToString
@Getter
public class ScheduleInfo {
    @Accessors(fluent = true)
    final boolean hasAccessibility;
    @Accessors(fluent = true)
    final boolean hasBikes;
    @Accessors(fluent = true)
    final boolean hasTravelModes;
    final ScheduleValidity scheduleValidity;
}
