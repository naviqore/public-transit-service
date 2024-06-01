package ch.naviqore.service.impl;

import ch.naviqore.service.Connection;
import ch.naviqore.service.Leg;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.List;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
@ToString
public class ConnectionImpl implements Connection {

    private final List<Leg> legs;

}
