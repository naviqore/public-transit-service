package org.naviqore.service.gtfs.raptor;

import lombok.Getter;
import lombok.ToString;
import org.naviqore.gtfs.schedule.model.Calendar;
import org.naviqore.gtfs.schedule.model.GtfsSchedule;
import org.naviqore.service.Validity;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

@Getter
@ToString
class GtfsRaptorValidity implements Validity {

    private final LocalDate startDate;
    private final LocalDate endDate;

    public GtfsRaptorValidity(GtfsSchedule schedule) {
        this.startDate = getMinOrMaxDate(schedule, Calendar::getStartDate, false);
        this.endDate = getMinOrMaxDate(schedule, Calendar::getEndDate, true);
    }

    @Override
    public boolean isWithin(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    private LocalDate getMinOrMaxDate(GtfsSchedule schedule, Function<Calendar, LocalDate> dateExtractor,
                                      boolean isMax) {
        // get the min or max date from the calendars if present
        Optional<LocalDate> calendarDate = schedule.getCalendars()
                .values()
                .stream()
                .map(dateExtractor)
                .filter(Objects::nonNull)
                .reduce((date1, date2) -> isMax ? date1.isAfter(date2) ? date1 : date2 : date1.isBefore(
                        date2) ? date1 : date2);

        // get the min or max date for all date exceptions if present
        Optional<LocalDate> calendarExceptionDate = schedule.getCalendars()
                .values()
                .stream()
                .flatMap(calendar -> calendar.getCalendarDates().keySet().stream())
                .reduce((date1, date2) -> isMax ? date1.isAfter(date2) ? date1 : date2 : date1.isBefore(
                        date2) ? date1 : date2);

        // get overall max or min value
        if (calendarDate.isEmpty() && calendarExceptionDate.isEmpty()) {
            // should never happen
            throw new IllegalStateException("No calendar start and end date found, if gtfs input is invalid.");
        } else if (calendarExceptionDate.isPresent() && calendarDate.isEmpty()) {
            return calendarExceptionDate.get();
        } else if (calendarExceptionDate.isEmpty()) {
            return calendarDate.get();
        } else if (isMax) { // both calendar and calendar date are present, get min or max
            return calendarDate.get()
                    .isAfter(calendarExceptionDate.get()) ? calendarDate.get() : calendarExceptionDate.get();
        } else {
            return calendarDate.get()
                    .isBefore(calendarExceptionDate.get()) ? calendarDate.get() : calendarExceptionDate.get();
        }

    }
}
