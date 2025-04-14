package org.naviqore.gtfs.schedule.model;

import lombok.*;
import org.jetbrains.annotations.Nullable;
import org.naviqore.gtfs.schedule.type.ExceptionType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
@EqualsAndHashCode(of = "id")
@ToString(of = {"id", "serviceDays", "startDate", "endDate"})
public final class Calendar implements Initializable {

    private final String id;
    private final EnumSet<DayOfWeek> serviceDays;
    @Nullable
    private final LocalDate startDate;
    @Nullable
    private final LocalDate endDate;
    private Map<LocalDate, CalendarDate> calendarDates = new HashMap<>();
    private List<Trip> trips = new ArrayList<>();

    /**
     * Determines if the service is operational on a specific day, considering both regular service days and
     * exceptions.
     *
     * @param date the date to check for service availability
     * @return true if the service is operational on the given date, false otherwise
     */
    public boolean isServiceAvailable(LocalDate date) {
        CalendarDate exception = calendarDates.get(date);
        if (exception != null) {
            return exception.type() == ExceptionType.ADDED;
        }
        // if no start and end date are defined, the calendar was added by calendar_dates.txt and is only active on
        // exception dates
        if (startDate == null || endDate == null) {
            return false;
        }
        if (date.isBefore(startDate) || date.isAfter(endDate)) {
            return false;
        }
        return serviceDays.contains(date.getDayOfWeek());
    }

    @Override
    public void initialize() {
        Collections.sort(trips);
        trips = List.copyOf(trips);
        calendarDates = Map.copyOf(calendarDates);
    }

    void addCalendarDate(CalendarDate calendarDate) {
        calendarDates.put(calendarDate.date(), calendarDate);
    }

    void addTrip(Trip trip) {
        trips.add(trip);
    }

}
