package org.naviqore.gtfs.schedule.model;

import org.naviqore.gtfs.schedule.type.ExceptionType;

import java.time.LocalDate;

public record CalendarDate(Calendar calendar, LocalDate date, ExceptionType type) implements Comparable<CalendarDate> {
    @Override
    public int compareTo(CalendarDate o) {
        return this.date.compareTo(o.date);
    }
}
