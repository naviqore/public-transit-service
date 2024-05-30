package ch.naviqore.service.impl;

import ch.naviqore.service.Leg;
import ch.naviqore.service.LegType;
import ch.naviqore.service.LegVisitor;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
@ToString
public abstract class LegImpl implements Leg {

    private final LegType legType;
    private final int distance;
    private final int duration;

    @Override
    public abstract <T> T accept(LegVisitor<T> visitor);

}
