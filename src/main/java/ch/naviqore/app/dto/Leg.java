package ch.naviqore.app.dto;

import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@EqualsAndHashCode
@ToString
@Getter
public class Leg {

    private final Location from;
    private final Location to;
    private final Stop fromStop;
    private final Stop toStop;
    private final LegType type;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private final LocalDateTime departureTime;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private final LocalDateTime arrivalTime;
    private final Trip trip;

}

