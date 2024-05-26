package ch.naviqore.app.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@RequiredArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString
@Getter
public class Route {

    private final String id;
    private final String name;
    private final String shortName;
    private final String transportMode;

}

