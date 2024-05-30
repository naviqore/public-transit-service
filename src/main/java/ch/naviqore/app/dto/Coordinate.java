package ch.naviqore.app.dto;

import lombok.*;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@EqualsAndHashCode
@ToString
@Getter
public class Coordinate {

    private final double latitude;
    private final double longitude;

}

