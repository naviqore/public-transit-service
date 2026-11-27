package org.naviqore.app.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
@Schema(description = "The dates a schedule covers; a request at a time on another date is refused with 400.")
public class ScheduleValidity {

    @Schema(description = "First date the schedule covers, as an ISO-8601 date.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    final LocalDate startDate;

    @Schema(description = "Last date the schedule covers, as an ISO-8601 date.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    final LocalDate endDate;

}
