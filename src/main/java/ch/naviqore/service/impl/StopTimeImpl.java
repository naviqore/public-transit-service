package ch.naviqore.service.impl;

import ch.naviqore.service.Stop;
import ch.naviqore.service.StopTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
@ToString
public class StopTimeImpl implements StopTime {

    private final Stop stop;
    private final LocalDateTime arrivalTime;
    private final LocalDateTime departureTime;

}
