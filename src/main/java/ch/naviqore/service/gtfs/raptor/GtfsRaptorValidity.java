package ch.naviqore.service.gtfs.raptor;

import ch.naviqore.gtfs.schedule.model.Calendar;
import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.service.Validity;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

@RequiredArgsConstructor
@Getter
@ToString
class GtfsRaptorValidity implements Validity {

    private final GtfsSchedule schedule;

    @Override
    public LocalDate getStartDate() {
        return getMinOrMaxDate(Calendar::getStartDate, false);
    }

    @Override
    public LocalDate getEndDate() {
        return getMinOrMaxDate(Calendar::getEndDate, true);
    }

    @Override
    public boolean isWithin(LocalDate date) {
        return schedule.getCalendars().values().stream().anyMatch(calendar -> calendar.isServiceAvailable(date));
    }

    private LocalDate getMinOrMaxDate(Function<Calendar, LocalDate> dateExtractor, boolean isMax) {
        // get the min or max date from the calendars' start or end dates
        Optional<LocalDate> calendarDate = schedule.getCalendars()
                .values()
                .stream()
                .map(dateExtractor)
                .filter(Objects::nonNull)
                .reduce((date1, date2) -> isMax ? date1.isAfter(date2) ? date1 : date2 : date1.isBefore(
                        date2) ? date1 : date2);

        // if no valid calendar dates are found, fall back to the exception dates
        return calendarDate.orElseGet(() -> schedule.getCalendars()
                .values()
                .stream()
                .flatMap(calendar -> calendar.getCalendarDates().keySet().stream())
                .reduce((date1, date2) -> isMax ? date1.isAfter(date2) ? date1 : date2 : date1.isBefore(
                        date2) ? date1 : date2)
                .orElseThrow(() -> new IllegalStateException("No valid dates found in the schedule")));
    }
}
