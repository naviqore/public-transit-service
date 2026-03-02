package org.naviqore.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;

/**
 * This class represents a connection between a stop and a spawn source (iso-line) in a transportation network. It
 * contains information about the stop, the leg closest to the target stop, and the connection itself.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
@EqualsAndHashCode
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StopConnection {

    private final Stop stop;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private final OffsetDateTime departureTime;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private final OffsetDateTime arrivalTime;
    private final int transfers;
    private final Leg connectingLeg;
    @Nullable
    private final Connection connection;

}