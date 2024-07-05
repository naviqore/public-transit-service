package ch.naviqore.raptor.router;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface RaptorTripMask {

    int earliestTripTime();

    int latestTripTime();

    boolean[] tripMask();

}
