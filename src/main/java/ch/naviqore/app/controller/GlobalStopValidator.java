package ch.naviqore.app.controller;

import ch.naviqore.service.ScheduleInformationService;
import ch.naviqore.service.Stop;
import ch.naviqore.service.exception.StopNotFoundException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@NoArgsConstructor(access = AccessLevel.NONE)
final class GlobalStopValidator {

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

    public enum StopType {
        SOURCE,
        TARGET,
        NOT_DEFINED
    }
}
