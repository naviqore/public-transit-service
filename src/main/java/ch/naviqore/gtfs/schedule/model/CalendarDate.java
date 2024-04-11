package ch.naviqore.gtfs.schedule.model;

import ch.naviqore.gtfs.schedule.type.ExceptionType;

import java.time.LocalDate;

public record CalendarDate(Calendar calendar, LocalDate date, ExceptionType type) {
}
