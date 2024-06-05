package ch.naviqore.service.impl;

import ch.naviqore.service.Stop;
import ch.naviqore.service.StopTime;
import ch.naviqore.service.Trip;
import lombok.*;

import java.time.LocalDateTime;

@AllArgsConstructor(access = AccessLevel.PACKAGE)
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
@ToString(exclude = "trip")
public class StopTimeImpl implements StopTime {

    private final Stop stop;
    private final LocalDateTime arrivalTime;
    private final LocalDateTime departureTime;
    @Setter(AccessLevel.PACKAGE)
    // cyclical dependency, therefore avoid serialization and use in toString representation
    private transient Trip trip;

}
