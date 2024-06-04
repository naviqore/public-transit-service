package ch.naviqore.app.dto;

import lombok.*;

import java.util.List;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@EqualsAndHashCode
@ToString
@Getter
public class Trip {

    private final String headSign;
    private final Route route;
    private final List<StopTime> stopTimes;

}

