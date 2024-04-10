package ch.naviqore.gtfs.schedule;

import java.time.LocalDate;

public record CalendarDate(Calendar calendar, LocalDate date, ExceptionType type) {
}
