package ch.naviqore.app.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@RequiredArgsConstructor
@EqualsAndHashCode
@ToString
@Getter
public class Coordinate {

    private final double latitude;
    private final double longitude;

}

