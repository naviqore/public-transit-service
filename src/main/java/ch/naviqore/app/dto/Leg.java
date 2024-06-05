package ch.naviqore.app.dto;

import ch.naviqore.utils.spatial.GeoCoordinate;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@EqualsAndHashCode
@ToString
@Getter
public class Leg {

    private final LegType type;
    private final GeoCoordinate from;
    private final GeoCoordinate to;
    private final Stop fromStop;
    private final Stop toStop;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private final LocalDateTime departureTime;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private final LocalDateTime arrivalTime;
    private final Trip trip;

}

