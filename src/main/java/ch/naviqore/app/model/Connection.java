package ch.naviqore.app.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.List;

@RequiredArgsConstructor
@EqualsAndHashCode
@ToString
@Getter
public class Connection {

    private final List<Leg> legs;

}

