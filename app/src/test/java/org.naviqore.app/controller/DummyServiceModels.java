package org.naviqore.app.controller;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;
import org.naviqore.service.LegType;
import org.naviqore.service.LegVisitor;
import org.naviqore.service.TravelMode;
import org.naviqore.service.WalkType;
import org.naviqore.utils.spatial.GeoCoordinate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

class DummyServiceModels {

    @RequiredArgsConstructor
    @Getter
    static abstract class Leg implements org.naviqore.service.Leg {
        private final LegType legType;
        private final int distance;
        private final int duration;

        @Override
        public abstract <T> T accept(LegVisitor<T> visitor);
    }

    @Getter
    static class PublicTransitLeg extends Leg implements org.naviqore.service.PublicTransitLeg {

        private final Trip trip;
        private final StopTime departure;
        private final StopTime arrival;

        PublicTransitLeg(int distance, int duration, Trip trip, StopTime departure, StopTime arrival) {
            super(LegType.PUBLIC_TRANSIT, distance, duration);
            this.trip = trip;
            this.departure = departure;
            this.arrival = arrival;
        }

        @Override
        public <T> T accept(LegVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    @Getter
    static class Transfer extends Leg implements org.naviqore.service.Transfer {
        private final LocalDateTime departureTime;
        private final LocalDateTime arrivalTime;
        private final Stop sourceStop;
        private final Stop targetStop;

        Transfer(int distance, int duration, LocalDateTime departureTime, LocalDateTime arrivalTime, Stop sourceStop,
                 Stop targetStop) {
            super(LegType.WALK, distance, duration);
            this.departureTime = departureTime;
            this.arrivalTime = arrivalTime;
            this.sourceStop = sourceStop;
            this.targetStop = targetStop;
        }

        @Override
        public <T> T accept(LegVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    @Getter
    static class Walk extends Leg implements org.naviqore.service.Walk {
        private final WalkType walkType;
        private final LocalDateTime departureTime;
        private final LocalDateTime arrivalTime;
        private final GeoCoordinate sourceLocation;
        private final GeoCoordinate targetLocation;
        private final Stop stop;

        Walk(int distance, int duration, WalkType walkType, LocalDateTime departureTime, LocalDateTime arrivalTime,
             GeoCoordinate sourceLocation, GeoCoordinate targetLocation, @Nullable Stop stop) {
            super(LegType.WALK, distance, duration);
            this.walkType = walkType;
            this.departureTime = departureTime;
            this.arrivalTime = arrivalTime;
            this.sourceLocation = sourceLocation;
            this.targetLocation = targetLocation;
            this.stop = stop;
        }

        @Override
        public <T> T accept(LegVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public Optional<org.naviqore.service.Stop> getStop() {
            return Optional.ofNullable(stop);
        }
    }

    @RequiredArgsConstructor
    @Getter
    static class Route implements org.naviqore.service.Route {
        private final String id;
        private final String name;
        private final String shortName;
        private final TravelMode routeType;
        private final String routeTypeDescription;
        private final String Agency;

    }

    @RequiredArgsConstructor
    @Getter
    static class Trip implements org.naviqore.service.Trip {
        private final String id;
        private final String headSign;
        private final Route route;
        private final boolean bikesAllowed;
        private final boolean wheelchairAccessible;
        @Setter
        private List<org.naviqore.service.StopTime> stopTimes;

    }

    @RequiredArgsConstructor
    @Getter
    static class Stop implements org.naviqore.service.Stop {
        private final String id;
        private final String name;
        private final GeoCoordinate coordinate;

    }

    @RequiredArgsConstructor
    @Getter
    static class StopTime implements org.naviqore.service.StopTime {
        private final Stop stop;
        private final LocalDateTime arrivalTime;
        private final LocalDateTime departureTime;
        private final transient Trip trip;
    }

    @RequiredArgsConstructor
    static class Connection implements org.naviqore.service.Connection {

        private final List<Leg> legs;

        @Override
        public List<org.naviqore.service.Leg> getLegs() {
            return legs.stream().map(leg -> (org.naviqore.service.Leg) leg).toList();
        }

        @Override
        public LocalDateTime getDepartureTime() {
            return legs.getFirst().accept(new LegVisitor<>() {
                @Override
                public LocalDateTime visit(org.naviqore.service.PublicTransitLeg publicTransitLeg) {
                    return publicTransitLeg.getDeparture().getDepartureTime();
                }

                @Override
                public LocalDateTime visit(org.naviqore.service.Transfer transfer) {
                    return transfer.getDepartureTime();
                }

                @Override
                public LocalDateTime visit(org.naviqore.service.Walk walk) {
                    return walk.getDepartureTime();
                }
            });
        }

        @Override
        public LocalDateTime getArrivalTime() {
            return legs.getLast().accept(new LegVisitor<>() {
                @Override
                public LocalDateTime visit(org.naviqore.service.PublicTransitLeg publicTransitLeg) {
                    return publicTransitLeg.getArrival().getArrivalTime();
                }

                @Override
                public LocalDateTime visit(org.naviqore.service.Transfer transfer) {
                    return transfer.getArrivalTime();
                }

                @Override
                public LocalDateTime visit(org.naviqore.service.Walk walk) {
                    return walk.getArrivalTime();
                }
            });
        }
    }
}
