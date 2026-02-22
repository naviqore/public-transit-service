package org.naviqore.service.gtfs.raptor;

import lombok.*;
import org.naviqore.service.Stop;
import org.naviqore.service.StopTime;
import org.naviqore.service.Trip;

import java.time.OffsetDateTime;

@AllArgsConstructor(access = AccessLevel.PACKAGE)
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
@ToString(exclude = "trip")
@EqualsAndHashCode
public class GtfsRaptorStopTime implements StopTime {

    private final Stop stop;
    private final OffsetDateTime arrivalTime;
    private final OffsetDateTime departureTime;
    @Setter(AccessLevel.PACKAGE)
    // cyclical dependency, therefore avoid serialization and use in toString representation
    private transient Trip trip;

}
