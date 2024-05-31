package ch.naviqore.service.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@RequiredArgsConstructor
@Getter
@ToString
public class ConnectionQueryConfig {

    private final int maximumWalkingDuration;
    private final int minimumTransferDuration;
    private final int maximumTransferNumber;
    private final int maximumTravelTime;

}
