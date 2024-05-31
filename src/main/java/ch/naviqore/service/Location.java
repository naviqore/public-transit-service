package ch.naviqore.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@RequiredArgsConstructor
@Getter
@ToString
public class Location {

    private final double latitude;
    private final double longitude;

}
