package org.naviqore.service.gtfs.raptor;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.naviqore.service.Leg;
import org.naviqore.service.LegType;
import org.naviqore.service.LegVisitor;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
@ToString
public abstract class GtfsRaptorLeg implements Leg {

    private final LegType legType;
    private final int distance;
    private final int duration;

    @Override
    public abstract <T> T accept(LegVisitor<T> visitor);

}
