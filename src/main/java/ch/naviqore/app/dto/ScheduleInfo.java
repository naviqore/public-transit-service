package ch.naviqore.app.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@RequiredArgsConstructor
@ToString
@Getter
public class ScheduleInfo {
    final boolean supportsAccessibility;
    final boolean supportsBikes;
    final boolean supportsTravelModes;
    final ScheduleValidity scheduleValidity;
}
