package org.naviqore.service.raptor;

import lombok.*;
import org.naviqore.service.Connection;
import org.naviqore.service.Leg;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * A connection with the time it leaves the origin and reaches the destination, which include the access and egress time
 * and a first or last walk too short to be a leg of its own.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
@ToString
@EqualsAndHashCode
public class RaptorServiceConnection implements Connection {

    private final List<Leg> legs;
    private final OffsetDateTime departureTime;
    private final OffsetDateTime arrivalTime;

}
