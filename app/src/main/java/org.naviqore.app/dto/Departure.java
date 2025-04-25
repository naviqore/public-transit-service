package org.naviqore.app.dto;

import lombok.*;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@EqualsAndHashCode
@ToString
@Getter
public class Departure {

    private final StopTime stopTime;
    private final Trip trip;

}

