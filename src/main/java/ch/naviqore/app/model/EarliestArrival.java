package ch.naviqore.app.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@RequiredArgsConstructor
@EqualsAndHashCode
@ToString
@Getter
public class EarliestArrival {

    private final Stop stop;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private final LocalDateTime arrivalTime;
    private final Connection connection;

}

