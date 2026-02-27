package org.naviqore.app.exception;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.naviqore.app.dto.StopType;

import java.util.Optional;

@Getter
public class StopNotFoundException extends ValidationException {

    private final String stopId;
    private final @Nullable StopType stopType;

    public StopNotFoundException(String stopId, @Nullable StopType stopType) {
        super(buildMessage(stopId, stopType));
        this.stopId = stopId;
        this.stopType = stopType;
    }

    private static String buildMessage(String stopId, @Nullable StopType stopType) {
        return stopType == null ? String.format("Stop with ID '%s' not found.", stopId) : String.format(
                "The requested %s stop with ID '%s' was not found.", stopType.name().toLowerCase(), stopId);
    }

    public Optional<StopType> getStopType() {
        return Optional.ofNullable(stopType);
    }
}



