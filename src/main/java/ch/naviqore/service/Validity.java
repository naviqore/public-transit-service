package ch.naviqore.service;

import java.time.LocalDate;

public interface Validity {

    LocalDate getStartDate();

    LocalDate getEndDate();

    boolean isWithin(LocalDate date);

}
