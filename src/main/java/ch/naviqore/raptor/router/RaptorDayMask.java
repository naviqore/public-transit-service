package ch.naviqore.raptor.router;

import java.time.LocalDate;
import java.util.Map;

public record RaptorDayMask(String serviceId, LocalDate date, int earliestTripTime, int latestTripTime,
                            Map<String, TripMask> tripMask) {
}