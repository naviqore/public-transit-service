package org.naviqore.app.controller;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.naviqore.service.ScheduleInformationService;
import org.naviqore.service.Stop;
import org.naviqore.service.exception.StopNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@NoArgsConstructor(access = AccessLevel.NONE)
final class GlobalValidator {

    public static LocalDateTime validateAndSetDefaultDateTime(LocalDateTime dateTime,
                                                              ScheduleInformationService service) {
        dateTime = (dateTime == null) ? LocalDateTime.now() : dateTime;

        if (service.getValidity().isWithin(dateTime.toLocalDate())) {
            return dateTime;
        }

        LocalDateTime startDate = service.getValidity().getStartDate().atStartOfDay();
        LocalDateTime endDate = service.getValidity().getEndDate().atTime(23, 59, 59);

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.format(
                "The provided datetime '%s' is outside of the schedule validity period. Please provide a datetime between '%s' and '%s'.",
                dateTime.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                startDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                endDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
    }

    public static Stop validateAndGetStop(String stopId, ScheduleInformationService service, StopType stopType) {
        try {
            return service.getStopById(stopId);
        } catch (StopNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, createErrorMessage(stopId, stopType), e);
        }
    }

    private static String createErrorMessage(String stopId, StopType stopType) {
        String stopTypeDescription = (stopType == StopType.NOT_DEFINED) ? "" : stopType.name().toLowerCase() + " ";
        return String.format("The requested %sstop with ID '%s' was not found.", stopTypeDescription, stopId);
    }

    enum StopType {
        SOURCE,
        TARGET,
        NOT_DEFINED
    }
}
