package ch.naviqore.service.impl;

import ch.naviqore.service.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.Optional;

@Getter
@ToString(callSuper = true)
public class WalkImpl extends LegImpl implements Walk {

    private final LocalDateTime departureTime;
    private final LocalDateTime arrivalTime;
    private final Location sourceLocation;
    private final Location targetLocation;
    @Getter(AccessLevel.NONE)
    @Nullable
    private final Stop sourceStop;
    @Getter(AccessLevel.NONE)
    @Nullable
    private final Stop targetStop;

    WalkImpl(int distance, int duration, LocalDateTime departureTime, LocalDateTime arrivalTime,
             Location sourceLocation, Location targetLocation, @Nullable Stop sourceStop, @Nullable Stop targetStop) {
        super(LegType.WALK, distance, duration);
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.sourceLocation = sourceLocation;
        this.targetLocation = targetLocation;
        this.sourceStop = sourceStop;
        this.targetStop = targetStop;
    }

    @Override
    public Optional<Stop> getSourceStop() {
        return Optional.ofNullable(sourceStop);
    }

    @Override
    public Optional<Stop> getTargetStop() {
        return Optional.ofNullable(targetStop);
    }

    @Override
    public <T> T accept(LegVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
