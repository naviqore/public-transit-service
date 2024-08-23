package ch.naviqore.app.dto;

import lombok.*;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@EqualsAndHashCode(of = "id")
@ToString
@Getter
public class Route {

    private final String id;
    private final String name;
    private final String shortName;
    private final TravelMode transportMode;
    private final String transportModeDescription;

}

