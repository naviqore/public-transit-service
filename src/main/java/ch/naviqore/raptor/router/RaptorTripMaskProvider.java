package ch.naviqore.raptor.router;

import java.time.LocalDate;
import java.util.Map;

public interface RaptorTripMaskProvider {
    void setTripIds(Map<String, String[]> routeTripIds);
    Map<String, TripMask> getTripMask(LocalDate date);
}
