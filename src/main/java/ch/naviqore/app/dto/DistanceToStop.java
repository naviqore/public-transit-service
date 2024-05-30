package ch.naviqore.app.dto;

import lombok.*;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@EqualsAndHashCode
@ToString
@Getter
public class DistanceToStop {

    private final Stop stop;
    private final double distance;

}

