package org.naviqore.service.gtfs.raptor;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.jspecify.annotations.Nullable;
import org.naviqore.service.*;
import org.naviqore.utils.spatial.GeoCoordinate;

import java.time.OffsetDateTime;
import java.util.Optional;

@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = false)
public class GtfsRaptorWalk extends GtfsRaptorLeg implements Walk {

    private final WalkType walkType;
    private final OffsetDateTime departureTime;
    private final OffsetDateTime arrivalTime;
    private final GeoCoordinate sourceLocation;
    private final GeoCoordinate targetLocation;
    @Getter(AccessLevel.NONE)
    @Nullable
    private final Stop stop;

    /**
     * Create a first or last mile walk between a stop and a location.
     */
    GtfsRaptorWalk(int distance, int duration, WalkType walkType, OffsetDateTime departureTime,
                   OffsetDateTime arrivalTime, GeoCoordinate sourceLocation, GeoCoordinate targetLocation,
                   @Nullable Stop stop) {
        super(LegType.WALK, distance, duration);
        this.walkType = walkType;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.sourceLocation = sourceLocation;
        this.targetLocation = targetLocation;
        this.stop = stop;
    }

    /**
     * Create a direct walk between two locations.
     */
    GtfsRaptorWalk(int distance, int duration, OffsetDateTime departureTime, OffsetDateTime arrivalTime,
                   GeoCoordinate sourceLocation, GeoCoordinate targetLocation) {
        this(distance, duration, WalkType.DIRECT, departureTime, arrivalTime, sourceLocation, targetLocation, null);
    }

    @Override
    public <T> T accept(LegVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public Optional<Stop> getStop() {
        return Optional.ofNullable(stop);
    }

}
