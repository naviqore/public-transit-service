package org.naviqore.app.dto;

import lombok.*;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@EqualsAndHashCode
@ToString
@Getter
public class StopEvent {

    private final StopTime stopTime;
    private final Trip trip;

}

