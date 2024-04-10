package ch.naviqore.gtfs.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;

public record Calendar(String id, EnumSet<DayOfWeek> serviceDays, LocalDate startDate, LocalDate endDate) {
}
