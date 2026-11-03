package org.naviqore.service.raptor;

import lombok.Getter;
import lombok.ToString;
import org.naviqore.service.Validity;
import org.naviqore.timetable.model.Timetable;

import java.time.LocalDate;

@Getter
@ToString
class RaptorServiceValidity implements Validity {

    private final LocalDate startDate;
    private final LocalDate endDate;

    public RaptorServiceValidity(Timetable schedule) {
        this.startDate = schedule.getStartDate();
        this.endDate = schedule.getEndDate();
    }

    @Override
    public boolean isWithin(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

}
