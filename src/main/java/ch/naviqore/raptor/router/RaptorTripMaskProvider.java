package ch.naviqore.raptor.router;

import java.time.LocalDate;
import java.util.Map;

public interface RaptorTripMaskProvider {
    void setTripIds(Map<String, String[]> routeTripIds);

    String getServiceIdForDate(LocalDate date);

    RaptorDayMask getTripMask(LocalDate date);
}
