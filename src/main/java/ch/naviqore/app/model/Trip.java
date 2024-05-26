package ch.naviqore.app.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.List;

@RequiredArgsConstructor
@EqualsAndHashCode
@ToString
@Getter
public class Trip {

    private final String headSign;
    private final Route route;
    private final List<StopTime> stopTimes;

}

