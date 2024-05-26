package ch.naviqore.app.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@RequiredArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString
@Getter
public class Stop {

    private final String id;
    private final String name;
    private final Coordinate coordinates;

}

