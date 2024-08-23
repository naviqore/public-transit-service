package ch.naviqore.app.dto;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
public class ScheduleValidity {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    final LocalDate startDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    final LocalDate endDate;
}
