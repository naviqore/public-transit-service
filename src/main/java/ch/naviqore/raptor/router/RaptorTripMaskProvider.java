package ch.naviqore.raptor.router;

import java.time.LocalDate;
import java.util.Map;

public interface RaptorTripMaskProvider {
    Map<String, RaptorTripMask> getTripMask(Map<String, String[]> routeTripIds, LocalDate date);
}
