package ch.naviqore.gtfs.schedule.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
public final class Calendar {
    private final String id;
    private final EnumSet<DayOfWeek> serviceDays;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final Map<LocalDate, CalendarDate> calendarDates = new HashMap<>();

    void addCalendarDate(CalendarDate calendarDate) {
        calendarDates.put(calendarDate.date(), calendarDate);
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
