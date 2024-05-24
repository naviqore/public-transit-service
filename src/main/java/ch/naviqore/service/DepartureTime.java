package ch.naviqore.service;

import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;

public interface DepartureTime {
    @NotNull LocalDate getDepartureTime();
}
