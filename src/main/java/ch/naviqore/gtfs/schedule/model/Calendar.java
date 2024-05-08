package ch.naviqore.gtfs.schedule.model;

import ch.naviqore.gtfs.schedule.type.ExceptionType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
public final class Calendar implements Initializable {

    private final String id;
    private final EnumSet<DayOfWeek> serviceDays;
    private final LocalDate startDate;
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
        if (date.isBefore(startDate) || date.isAfter(endDate)) {
            return false;
        }
        CalendarDate exception = calendarDates.get(date);
        if (exception != null) {
            return exception.type() == ExceptionType.ADDED;
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

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Calendar) obj;
        return Objects.equals(this.id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Calendar[" + "id=" + id + ", " + "serviceDays=" + serviceDays + ", " + "startDate=" + startDate + ", " + "endDate=" + endDate + ']';
    }

}
