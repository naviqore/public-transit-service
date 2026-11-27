package org.naviqore.raptor.router;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.naviqore.raptor.TimeType;

import java.util.*;
import java.util.function.ToIntFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.naviqore.raptor.router.QueryState.NO_INDEX;

/**
 * The transfers between particular routes or trips, compiled for the search (see {@link TripTransfer}).
 * <p>
 * A trip transfer applies between the routes of the router at two stops, or at one: from the trips of one route
 * arriving at a stop to the trips of another route departing from a stop. The trip transfers of such a pair of routes
 * at a pair of stops form a group. In a group, the most specific trip transfer applies to a pair of trips, and where
 * none does, the walk or the same stop transfer time of the stops.
 * <p>
 * The search changes along a group from every trip of its route that reaches the stop the group starts at (routing
 * backward, that leaves the stop it ends at), see {@link RouteScanner}. A group is restrictive if a trip transfer of it
 * refuses a change, or takes longer than the stop, that the stop allows. An arrival on a route with a restrictive group
 * is kept apart from the arrivals at the stop, in a slot of its own: an index after the stops, which the query state
 * and the search treat like a stop with the id, routes and walks of the stop. So a trip transfer never hides an arrival
 * on another route that changes as the stop allows. A walk from such an arrival to a stop a restrictive group of its
 * route ends at lands in a slot too. Any other arrival can change as the stop allows, and stays at the stop. A route is
 * boarded by the walks and same stop transfer times of a stop after the arrivals at the stop and in its slots, except
 * after an arrival on a trip that a trip transfer of a restrictive group to the route applies to: that trip changes by
 * the rules of the group.
 */
@Slf4j
final class TripTransfers {

    /**
     * The trip transfer takes no time and no minimum transfer duration: the rider stays in the vehicle.
     */
    static final int IN_SEAT = -1;
    /**
     * The trip transfer is not possible.
     */
    static final int NOT_POSSIBLE = -2;
    /**
     * The trip transfer takes the time of the stops, as if they did not refuse transfers.
     */
    static final int STOP_TIME = -3;
    /**
     * No trip transfer applies: the walk or the same stop transfer time of the stops does.
     */
    static final int DEFAULT = -4;

    private static final int FORWARD = 0;
    private static final int BACKWARD = 1;
    private static final Group[] NO_GROUPS = new Group[0];
    private static final int[] NO_SLOTS = new int[0];
    private static final int[] NO_OFFSETS = new int[0];

    private final int numberOfStops;
    private final int numberOfGroups;
    // per slot: the stop it is at, the routes a group of its arrival ends at there, sorted, and those groups
    private final int[] slotStops;
    private final int[][] governedRoutes;
    private final Group[][] governingGroups;
    // per slot of an arrival: where each walk of its stop lands, by the walk's offset among them, and the walks that
    // land in slots of its own
    private final int[][] walkTargets;
    private final int[][] walksToOwnSlots;
    // per direction and route stop: the slot of the arrivals of the route there, and the groups it starts
    private final int[][] arrivalSlots;
    private final Group[][][] groups;
    // per direction and stop: the slots at the stop
    private final int[][][] stopSlots;
    // per direction: the routes with a stop that has slots or starts groups, and those route stops
    private final boolean[][] routesWithTransfers;
    private final BitSet[] routeStopsWithTransfers;

    private TripTransfers(int numberOfStops, int numberOfGroups, int[] slotStops, int[][] governedRoutes,
                          Group[][] governingGroups, int[][] walkTargets, int[][] walksToOwnSlots, int[][] arrivalSlots,
                          Group[][][] groups, int[][][] stopSlots, boolean[][] routesWithTransfers,
                          BitSet[] routeStopsWithTransfers) {
        this.numberOfStops = numberOfStops;
        this.numberOfGroups = numberOfGroups;
        this.slotStops = slotStops;
        this.governedRoutes = governedRoutes;
        this.governingGroups = governingGroups;
        this.walkTargets = walkTargets;
        this.walksToOwnSlots = walksToOwnSlots;
        this.arrivalSlots = arrivalSlots;
        this.groups = groups;
        this.stopSlots = stopSlots;
        this.routesWithTransfers = routesWithTransfers;
        this.routeStopsWithTransfers = routeStopsWithTransfers;
    }

    /**
     * Without any trip transfer.
     */
    static TripTransfers none(int numberOfStops, int numberOfRouteStops, int numberOfRoutes) {
        return new TripTransfers(numberOfStops, 0, NO_SLOTS, new int[0][], new Group[0][], new int[0][], new int[0][],
                new int[][]{filled(numberOfRouteStops), filled(numberOfRouteStops)}, new Group[2][][], new int[2][][],
                new boolean[][]{new boolean[numberOfRoutes], new boolean[numberOfRoutes]},
                new BitSet[]{new BitSet(), new BitSet()});
    }

    private static int[] filled(int size) {
        int[] array = new int[size];
        Arrays.fill(array, NO_INDEX);
        return array;
    }

    private static int direction(TimeType timeType) {
        return timeType == TimeType.DEPARTURE ? FORWARD : BACKWARD;
    }

    /**
     * The number of groups, each with an {@link Group#index} below it.
     */
    int numberOfGroups() {
        return numberOfGroups;
    }

    /**
     * The number of slots, which follow the stops.
     */
    int numberOfSlots() {
        return slotStops.length;
    }

    /**
     * The stop of a stop or slot index.
     */
    int stopOf(int index) {
        return index < numberOfStops ? index : slotStops[index - numberOfStops];
    }

    /**
     * Whether any stop of a route has slots or starts groups, for the search in a direction.
     */
    boolean hasTransfers(TimeType timeType, int routeIdx) {
        return routesWithTransfers[direction(timeType)][routeIdx];
    }

    /**
     * Whether the stop of a route stop has slots, or the route stop starts groups, for the search in a direction.
     */
    boolean hasTransfersAt(TimeType timeType, int routeStopIdx) {
        return routeStopsWithTransfers[direction(timeType)].get(routeStopIdx);
    }

    /**
     * The index a trip of a route reaches a stop at: the slot of the route there, or the stop.
     *
     * @param routeStopIdx the index of the route stop.
     * @param stopIdx      the index of its stop.
     */
    int arrivalIndex(TimeType timeType, int routeStopIdx, int stopIdx) {
        int slot = arrivalSlots[direction(timeType)][routeStopIdx];
        return slot == NO_INDEX ? stopIdx : slot;
    }

    /**
     * The groups a route stop starts: those from the route at the stop, or routing backward, those to it.
     */
    Group @Nullable [] groups(TimeType timeType, int routeStopIdx) {
        Group[][] directionGroups = groups[direction(timeType)];
        return directionGroups == null ? null : directionGroups[routeStopIdx];
    }

    /**
     * The slots at a stop, or null if it has none.
     */
    int @Nullable [] slots(TimeType timeType, int stopIdx) {
        int[][] directionSlots = stopSlots[direction(timeType)];
        return directionSlots == null ? null : directionSlots[stopIdx];
    }

    /**
     * The group the arrival of a slot changes to a route along, or null if the transfers of its stop apply to all its
     * trips.
     */
    @Nullable Group governingGroup(int slot, int routeIdx) {
        int i = Arrays.binarySearch(governedRoutes[slot - numberOfStops], routeIdx);
        return i < 0 ? null : governingGroups[slot - numberOfStops][i];
    }

    /**
     * The index a walk from a stop or slot to a stop lands at: a slot of the arrival walked from at that stop, or the
     * stop.
     *
     * @param walk the offset of the walk among the walks of the stop walked from, in the direction of the search.
     */
    int walkTarget(int sourceIdx, int walk, int targetStopIdx) {
        if (sourceIdx < numberOfStops) {
            return targetStopIdx;
        }
        int[] targets = walkTargets[sourceIdx - numberOfStops];
        return targets == null ? targetStopIdx : targets[walk];
    }

    /**
     * The walks from a slot that land in slots of its own, by their offset among the walks of its stop.
     */
    int[] walksToOwnSlots(int slot) {
        int[] walks = walksToOwnSlots[slot - numberOfStops];
        return walks == null ? NO_SLOTS : walks;
    }

    /**
     * The trip transfers from the trips of one route at a stop to those of another route at a stop.
     * <p>
     * The rule of a pair of trips is a minimum time in seconds, or {@link #IN_SEAT}, {@link #NOT_POSSIBLE},
     * {@link #STOP_TIME} or {@link #DEFAULT}.
     */
    static final class Group {

        private static final long[] NO_PAIRS = new long[0];
        private static final int NO_RULE = Integer.MIN_VALUE;

        final int fromStopIdx;
        final int fromRouteIdx;
        final int toStopIdx;
        final int toRouteIdx;
        private final Map<Long, Integer> pairMap = new HashMap<>();
        private final Map<Integer, Integer> fromTripRules = new HashMap<>();
        private final Map<Integer, Integer> toTripRules = new HashMap<>();
        private final Map<Integer, Integer> fromTripAnyRules = new HashMap<>();
        private final Map<Integer, Integer> toTripAnyRules = new HashMap<>();
        // the number of the group among the groups of the router
        int index = NO_INDEX;
        // where the search boards the route the group ends at (routing backward: starts from): at each of its calls at
        // the stop, since a trip reached too late for one call may be reached in time for a later one
        int[] toOffsets = NO_OFFSETS;
        int[] fromOffsets = NO_OFFSETS;
        // whether a trip transfer of the group refuses a change, or takes longer than the stops, that the stops allow
        boolean restrictive;
        // the walk or same stop transfer time of the stops, respecting a refusal, and as if they did not refuse one
        int defaultTime = NOT_POSSIBLE;
        int stopTime = NOT_POSSIBLE;
        // by specificity: both trips, a trip with the other route, a trip with any route, both routes or one; the pairs
        // of trips sorted by the trip they start from, and by the one they end at
        private long[] pairs = NO_PAIRS;
        private int[] pairRules = new int[0];
        // the positions of the trips the trip transfers name, on the route the group starts from and the one it ends at
        private int[] fromPositions = NO_SLOTS;
        private int[] toPositions = NO_SLOTS;
        private int routeRule = NO_RULE;
        private int routeRuleSpecificity = Integer.MAX_VALUE;

        Group(int fromStopIdx, int fromRouteIdx, int toStopIdx, int toRouteIdx) {
            this.fromStopIdx = fromStopIdx;
            this.fromRouteIdx = fromRouteIdx;
            this.toStopIdx = toStopIdx;
            this.toRouteIdx = toRouteIdx;
        }

        private static long pair(int fromPosition, int toPosition) {
            return (long) fromPosition << 32 | (toPosition & 0xFFFFFFFFL);
        }

        // the maps are mostly empty: then a look-up neither boxes nor hashes the position
        private static @Nullable Integer get(Map<Integer, Integer> rules, int position) {
            return rules.isEmpty() ? null : rules.get(position);
        }

        private static IntStream keys(Map<Integer, Integer> rules) {
            return rules.keySet().stream().mapToInt(Integer::intValue);
        }

        /**
         * Whether a trip transfer of the group applies to a trip: one of the routes, or one naming the trip or a trip
         * of the other route. A trip without changes as the stops allow.
         *
         * @param knownPosition the position of the trip on its route.
         * @param from          whether the trip is the one the group starts from, or the one it ends at.
         */
        boolean hasRules(int knownPosition, boolean from) {
            return routeRule != NO_RULE || hasTripRules(knownPosition, from);
        }

        /**
         * Whether a trip of the group may change to any trip of the other route: always if the trip transfers of the
         * routes, or else the stops, allow a change; otherwise only if a trip transfer names the trip or a trip of the
         * other route.
         *
         * @param knownPosition the position of the trip on its route.
         * @param from          whether the trip is the one the group starts from, or the one it ends at.
         */
        boolean mayChange(int knownPosition, boolean from) {
            int genericRule = switch (routeRule) {
                case NO_RULE -> defaultTime;
                case STOP_TIME -> stopTime;
                default -> routeRule;
            };
            return genericRule != NOT_POSSIBLE || hasTripRules(knownPosition, from);
        }

        /**
         * Whether a trip transfer names the trip, or a trip of the other route.
         */
        private boolean hasTripRules(int knownPosition, boolean from) {
            if (from) {
                return Arrays.binarySearch(fromPositions,
                        knownPosition) >= 0 || !toTripRules.isEmpty() || !toTripAnyRules.isEmpty();
            }
            return Arrays.binarySearch(toPositions,
                    knownPosition) >= 0 || !fromTripRules.isEmpty() || !fromTripAnyRules.isEmpty();
        }

        boolean isAtOneStop() {
            return fromStopIdx == toStopIdx;
        }

        /**
         * Whether a rule refuses a change, or takes longer than the stops, that the stops allow.
         */
        private boolean restricts(int rule) {
            int time = rule == STOP_TIME ? stopTime : rule;
            if (rule == IN_SEAT || defaultTime == NOT_POSSIBLE) {
                return false;
            }
            return time == NOT_POSSIBLE || time > defaultTime;
        }

        private boolean isRestrictive() {
            if (routeRule != NO_RULE && restricts(routeRule)) {
                return true;
            }
            return Arrays.stream(pairRules).anyMatch(this::restricts) || Stream.of(fromTripRules, toTripRules,
                            fromTripAnyRules, toTripAnyRules)
                    .flatMap(rules -> rules.values().stream())
                    .anyMatch(this::restricts);
        }

        /**
         * Gets the rule of a pair of trips, by their positions on their routes.
         */
        int rule(int fromPosition, int toPosition) {
            int i = Arrays.binarySearch(pairs, pair(fromPosition, toPosition));
            if (i >= 0) {
                return pairRules[i];
            }
            Integer rule = get(fromTripRules, fromPosition);
            if (rule == null) {
                rule = get(toTripRules, toPosition);
            }
            if (rule == null) {
                rule = get(fromTripAnyRules, fromPosition);
            }
            if (rule == null) {
                rule = get(toTripAnyRules, toPosition);
            }
            if (rule != null) {
                return rule;
            }
            return routeRule == NO_RULE ? DEFAULT : routeRule;
        }

        private void add(int specificity, int fromPosition, int toPosition, int rule) {
            switch (specificity) {
                case 1 -> pairMap.put(pair(fromPosition, toPosition), rule);
                case 2 -> {
                    if (fromPosition != NO_INDEX) {
                        fromTripRules.put(fromPosition, rule);
                    } else {
                        toTripRules.put(toPosition, rule);
                    }
                }
                case 3 -> {
                    if (fromPosition != NO_INDEX) {
                        fromTripAnyRules.put(fromPosition, rule);
                    } else {
                        toTripAnyRules.put(toPosition, rule);
                    }
                }
                default -> {
                    // both routes (4) rank above one route (5); of two equally specific ones, the later
                    if (specificity <= routeRuleSpecificity) {
                        routeRule = rule;
                        routeRuleSpecificity = specificity;
                    }
                }
            }
        }

        /**
         * Sorts the trip transfers of both trips for their look-up.
         */
        private void complete() {
            pairs = pairMap.keySet().stream().mapToLong(Long::longValue).sorted().toArray();
            pairRules = new int[pairs.length];
            for (int i = 0; i < pairs.length; i++) {
                pairRules[i] = Objects.requireNonNull(pairMap.get(pairs[i]));
            }
            pairMap.clear();
            fromPositions = IntStream.concat(Arrays.stream(pairs).mapToInt(pair -> (int) (pair >>> 32)),
                    IntStream.concat(keys(fromTripRules), keys(fromTripAnyRules))).distinct().sorted().toArray();
            toPositions = IntStream.concat(Arrays.stream(pairs).mapToInt(pair -> (int) pair),
                    IntStream.concat(keys(toTripRules), keys(toTripAnyRules))).distinct().sorted().toArray();
        }

    }

    /**
     * Compiles the trip transfers of a router.
     */
    static final class Builder {

        private final Lookup lookup;
        private final StopContext stopContext;
        private final RouteTraversal routeTraversal;
        private final Set<Integer> refusingStops;
        private final Map<Long, Integer> refusedWalks;
        private final Map<String, int[]> trips = new HashMap<>();
        // the groups by their stops and routes: from stop, from route, to stop and to route, in that order
        private final TreeMap<List<Integer>, Group> groups = new TreeMap<>(Builder::compareKeys);

        /**
         * @param refusingStops the stops that refuse transfers.
         * @param refusedWalks  the walks between stops that refuse transfers, by their source and target stop, with the
         *                      time they would take.
         */
        Builder(Lookup lookup, StopContext stopContext, RouteTraversal routeTraversal, Set<Integer> refusingStops,
                Map<Long, Integer> refusedWalks) {
            this.lookup = lookup;
            this.stopContext = stopContext;
            this.routeTraversal = routeTraversal;
            this.refusingStops = refusingStops;
            this.refusedWalks = refusedWalks;
            Route[] routes = routeTraversal.routes();
            for (int routeIdx = 0; routeIdx < routes.length; routeIdx++) {
                String[] tripIds = routes[routeIdx].tripIds();
                for (int position = 0; position < tripIds.length; position++) {
                    trips.put(tripIds[position], new int[]{routeIdx, position});
                }
            }
        }

        private static int compareKeys(List<Integer> key, List<Integer> otherKey) {
            for (int i = 0; i < key.size(); i++) {
                int comparison = Integer.compare(key.get(i), otherKey.get(i));
                if (comparison != 0) {
                    return comparison;
                }
            }
            return 0;
        }

        static long walk(int sourceStopIdx, int targetStopIdx) {
            return (long) sourceStopIdx << 32 | (targetStopIdx & 0xFFFFFFFFL);
        }

        private static int encode(TripTransfer transfer) {
            return switch (transfer.type()) {
                case MINIMUM_TIME -> transfer.duration();
                case STOP_TIME -> STOP_TIME;
                case IN_SEAT -> IN_SEAT;
                case NOT_POSSIBLE -> NOT_POSSIBLE;
            };
        }

        void add(TripTransfer transfer) {
            int fromStopIdx = Objects.requireNonNull(lookup.stops().get(transfer.fromStopId()));
            int toStopIdx = Objects.requireNonNull(lookup.stops().get(transfer.toStopId()));
            List<int[]> from = resolve(transfer.fromRouteId(), transfer.fromTripId(), fromStopIdx);
            List<int[]> to = resolve(transfer.toRouteId(), transfer.toTripId(), toStopIdx);
            boolean fromTrip = transfer.fromTripId() != null;
            boolean toTrip = transfer.toTripId() != null;
            boolean fromRoute = fromTrip || transfer.fromRouteId() != null;
            boolean toRoute = toTrip || transfer.toRouteId() != null;
            int specificity;
            if (fromTrip && toTrip) {
                specificity = 1;
            } else if ((fromTrip && toRoute) || (toTrip && fromRoute)) {
                specificity = 2;
            } else if (fromTrip || toTrip) {
                specificity = 3;
            } else if (fromRoute && toRoute) {
                specificity = 4;
            } else {
                specificity = 5;
            }

            int rule = encode(transfer);
            for (int[] fromTrips : from) {
                for (int[] toTrips : to) {
                    group(fromStopIdx, fromTrips[0], toStopIdx, toTrips[0]).add(specificity, fromTrips[1], toTrips[1],
                            rule);
                }
            }
        }

        /**
         * Resolves a side of a trip transfer to routes, each with the position of the trip on it, or NO_INDEX: the
         * route of the trip, the routes of the route, or every route at the stop.
         *
         * @throws IllegalArgumentException if the router does not have the route or the trip, or the trip is of another
         *                                  route.
         */
        private List<int[]> resolve(@Nullable String routeId, @Nullable String tripId, int stopIdx) {
            if (tripId != null) {
                int[] trip = trips.get(tripId);
                if (trip == null) {
                    throw new IllegalArgumentException("Trip " + tripId + " does not exist");
                }
                if (routeId != null && !routeTraversal.routes()[trip[0]].id().equals(routeId)) {
                    throw new IllegalArgumentException("Trip " + tripId + " does not belong to route " + routeId);
                }
                return List.of(trip);
            }

            List<int[]> routes = new ArrayList<>();
            if (routeId != null) {
                int[] routeIndices = lookup.routes().get(routeId);
                if (routeIndices == null) {
                    throw new IllegalArgumentException("Route " + routeId + " does not exist");
                }
                for (int routeIdx : routeIndices) {
                    routes.add(new int[]{routeIdx, NO_INDEX});
                }
            } else {
                Stop stop = stopContext.stops()[stopIdx];
                for (int i = stop.stopRouteIdx(); i < stop.stopRouteIdx() + stop.numberOfRoutes(); i++) {
                    routes.add(new int[]{stopContext.stopRoutes()[i], NO_INDEX});
                }
            }
            return routes;
        }

        private Group group(int fromStopIdx, int fromRouteIdx, int toStopIdx, int toRouteIdx) {
            return groups.computeIfAbsent(List.of(fromStopIdx, fromRouteIdx, toStopIdx, toRouteIdx),
                    _ -> new Group(fromStopIdx, fromRouteIdx, toStopIdx, toRouteIdx));
        }

        /**
         * Finds where a route calls at a stop: the positions it picks up passengers at, but not at its end, or for the
         * route a group starts from, those it sets them down at, but not at its start.
         *
         * @param to whether the route is the one the group ends at.
         */
        private int[] findOffsets(int routeIdx, int stopIdx, boolean to) {
            Route route = routeTraversal.routes()[routeIdx];
            int numberOfStops = route.numberOfStops();
            return IntStream.range(0, numberOfStops).filter(offset -> {
                RouteStop routeStop = routeTraversal.routeStops()[route.firstRouteStopIdx() + offset];
                if (routeStop.stopIndex() != stopIdx) {
                    return false;
                }
                return to ? offset < numberOfStops - 1 && routeStop.boarding() : offset > 0 && routeStop.alighting();
            }).toArray();
        }

        /**
         * Finds the walk between two stops, or NOT_POSSIBLE.
         */
        private int findWalk(int sourceStopIdx, int targetStopIdx) {
            Stop stop = stopContext.stops()[sourceStopIdx];
            Transfer[] transfers = stopContext.transfers();
            for (int i = stop.transferIdx(); i >= 0 && i < stop.transferIdx() + stop.numberOfTransfers(); i++) {
                if (transfers[i].targetStopIdx() == targetStopIdx) {
                    return transfers[i].duration();
                }
            }
            return NOT_POSSIBLE;
        }

        TripTransfers build() {
            int numberOfStops = stopContext.stops().length;
            int numberOfRouteStops = routeTraversal.routeStops().length;
            int numberOfRoutes = routeTraversal.routes().length;
            if (groups.isEmpty()) {
                return none(numberOfStops, numberOfRouteStops, numberOfRoutes);
            }

            int numberOfGroups = 0;
            for (Group group : groups.values()) {
                group.index = numberOfGroups++;
                group.toOffsets = findOffsets(group.toRouteIdx, group.toStopIdx, true);
                group.fromOffsets = findOffsets(group.fromRouteIdx, group.fromStopIdx, false);
                if (group.isAtOneStop()) {
                    int sameStopTime = stopContext.stops()[group.fromStopIdx].sameStopTransferTime();
                    group.stopTime = sameStopTime;
                    group.defaultTime = refusingStops.contains(group.fromStopIdx) ? NOT_POSSIBLE : sameStopTime;
                } else {
                    group.defaultTime = findWalk(group.fromStopIdx, group.toStopIdx);
                    Integer refused = refusedWalks.get(walk(group.fromStopIdx, group.toStopIdx));
                    group.stopTime = refused == null ? group.defaultTime : refused;
                }
                group.complete();
                group.restrictive = group.isRestrictive();
            }

            // the origins of the groups: forward the route at the stop a group starts at, backward the one it ends at
            Map<List<Integer>, List<Group>> forwardGroups = new LinkedHashMap<>();
            Map<List<Integer>, List<Group>> backwardGroups = new LinkedHashMap<>();
            for (Group group : groups.values()) {
                List<Integer> forwardOrigin = List.of(group.fromStopIdx, group.fromRouteIdx);
                List<Integer> backwardOrigin = List.of(group.toStopIdx, group.toRouteIdx);
                forwardGroups.computeIfAbsent(forwardOrigin, _ -> new ArrayList<>()).add(group);
                backwardGroups.computeIfAbsent(backwardOrigin, _ -> new ArrayList<>()).add(group);
            }
            List<List<Integer>> forwardOrigins = new ArrayList<>(forwardGroups.keySet());
            List<List<Integer>> backwardOrigins = new ArrayList<>(backwardGroups.keySet());
            forwardOrigins.sort(Builder::compareKeys);
            backwardOrigins.sort(Builder::compareKeys);

            // the slots: of the arrivals of each origin, and of the walks from them to the stops their groups end at
            List<Integer> slotStops = new ArrayList<>();
            List<Group[]> governing = new ArrayList<>();
            List<int[]> walkStops = new ArrayList<>();
            List<int[]> walkSlotIndices = new ArrayList<>();
            int[][] arrivalSlots = {filled(numberOfRouteStops), filled(numberOfRouteStops)};
            Group[][][] routeStopGroups = {new Group[numberOfRouteStops][], new Group[numberOfRouteStops][]};
            Map<Integer, List<Integer>> forwardStopSlots = new TreeMap<>();
            Map<Integer, List<Integer>> backwardStopSlots = new TreeMap<>();
            // the slots of the search departing come first
            int forwardSlots = 0;

            for (int d = FORWARD; d <= BACKWARD; d++) {
                boolean forward = d == FORWARD;
                if (!forward) {
                    forwardSlots = slotStops.size();
                }
                Map<List<Integer>, List<Group>> originGroups = forward ? forwardGroups : backwardGroups;
                Map<Integer, List<Integer>> stopSlots = forward ? forwardStopSlots : backwardStopSlots;
                for (List<Integer> origin : forward ? forwardOrigins : backwardOrigins) {
                    int stopIdx = origin.get(0);
                    int routeIdx = origin.get(1);
                    List<Group> originGroupList = Objects.requireNonNull(originGroups.get(origin));
                    Route route = routeTraversal.routes()[routeIdx];

                    // the route stops of the origin start its groups
                    for (int offset = 0; offset < route.numberOfStops(); offset++) {
                        int routeStopIdx = route.firstRouteStopIdx() + offset;
                        if (routeTraversal.routeStops()[routeStopIdx].stopIndex() == stopIdx) {
                            routeStopGroups[d][routeStopIdx] = originGroupList.toArray(NO_GROUPS);
                        }
                    }
                    if (originGroupList.stream().noneMatch(group -> group.restrictive)) {
                        continue;
                    }

                    // the arrival slot: the restrictive groups at the stop itself
                    int arrivalSlot = numberOfStops + slotStops.size();
                    slotStops.add(stopIdx);
                    governing.add(originGroupList.stream()
                            .filter(group -> group.restrictive && group.isAtOneStop())
                            .toArray(Group[]::new));
                    stopSlots.computeIfAbsent(stopIdx, _ -> new ArrayList<>()).add(arrivalSlot);

                    // the walk slots, at the other stops of the restrictive groups
                    TreeMap<Integer, List<Group>> byOtherStop = new TreeMap<>();
                    for (Group group : originGroupList) {
                        if (group.restrictive && !group.isAtOneStop()) {
                            byOtherStop.computeIfAbsent(forward ? group.toStopIdx : group.fromStopIdx,
                                    _ -> new ArrayList<>()).add(group);
                        }
                    }
                    int[] targets = new int[byOtherStop.size()];
                    int[] targetSlots = new int[byOtherStop.size()];
                    walkStops.add(targets);
                    walkSlotIndices.add(targetSlots);
                    int i = 0;
                    for (Map.Entry<Integer, List<Group>> entry : byOtherStop.entrySet()) {
                        int walkSlot = numberOfStops + slotStops.size();
                        slotStops.add(entry.getKey());
                        governing.add(entry.getValue().toArray(Group[]::new));
                        walkStops.add(null);
                        walkSlotIndices.add(null);
                        stopSlots.computeIfAbsent(entry.getKey(), _ -> new ArrayList<>()).add(walkSlot);
                        targets[i] = entry.getKey();
                        targetSlots[i++] = walkSlot;
                    }

                    // the arrivals at the route stops of the origin go to the slot
                    for (int offset = 0; offset < route.numberOfStops(); offset++) {
                        int routeStopIdx = route.firstRouteStopIdx() + offset;
                        if (routeTraversal.routeStops()[routeStopIdx].stopIndex() == stopIdx) {
                            arrivalSlots[d][routeStopIdx] = arrivalSlot;
                        }
                    }
                }
            }

            int[][][] stopSlots = {new int[numberOfStops][], new int[numberOfStops][]};
            forwardStopSlots.forEach((stopIdx, slots) -> stopSlots[FORWARD][stopIdx] = slots.stream()
                    .mapToInt(Integer::intValue)
                    .toArray());
            backwardStopSlots.forEach((stopIdx, slots) -> stopSlots[BACKWARD][stopIdx] = slots.stream()
                    .mapToInt(Integer::intValue)
                    .toArray());

            boolean[][] routesWithTransfers = {new boolean[numberOfRoutes], new boolean[numberOfRoutes]};
            BitSet[] routeStopsWithTransfers = {new BitSet(numberOfRouteStops), new BitSet(numberOfRouteStops)};
            Route[] routes = routeTraversal.routes();
            for (int d = FORWARD; d <= BACKWARD; d++) {
                for (int routeIdx = 0; routeIdx < numberOfRoutes; routeIdx++) {
                    Route route = routes[routeIdx];
                    for (int offset = 0; offset < route.numberOfStops(); offset++) {
                        int routeStopIdx = route.firstRouteStopIdx() + offset;
                        int stopIdx = routeTraversal.routeStops()[routeStopIdx].stopIndex();
                        if (routeStopGroups[d][routeStopIdx] != null || stopSlots[d][stopIdx] != null) {
                            routesWithTransfers[d][routeIdx] = true;
                            routeStopsWithTransfers[d].set(routeStopIdx);
                        }
                    }
                }
            }

            // per slot of an arrival, where each walk of its stop lands: the walk slots of the stops its groups end at
            int[][] walkTargets = new int[slotStops.size()][];
            int[][] walksToOwnSlots = new int[slotStops.size()][];
            for (int slot = 0; slot < slotStops.size(); slot++) {
                int[] targetStops = walkStops.get(slot);
                if (targetStops == null || targetStops.length == 0) {
                    continue;
                }
                TimeType timeType = slot < forwardSlots ? TimeType.DEPARTURE : TimeType.ARRIVAL;
                Stop stop = stopContext.stops()[slotStops.get(slot)];
                Transfer[] walks = stopContext.transfers(timeType);
                int[] targets = new int[stop.numberOfTransfers(timeType)];
                List<Integer> toOwnSlots = new ArrayList<>();
                for (int walk = 0; walk < targets.length; walk++) {
                    int targetStopIdx = walks[stop.transferIdx(timeType) + walk].targetStopIdx();
                    int i = Arrays.binarySearch(targetStops, targetStopIdx);
                    targets[walk] = i < 0 ? targetStopIdx : walkSlotIndices.get(slot)[i];
                    if (i >= 0) {
                        toOwnSlots.add(walk);
                    }
                }
                walkTargets[slot] = targets;
                walksToOwnSlots[slot] = toOwnSlots.stream().mapToInt(Integer::intValue).toArray();
            }

            // per slot, the groups by the route they end at (routing backward: start from), each one route at the stop
            int[][] governedRoutes = new int[governing.size()][];
            Group[][] governingGroups = new Group[governing.size()][];
            for (int slot = 0; slot < governing.size(); slot++) {
                ToIntFunction<Group> governedRoute = slot < forwardSlots ? group -> group.toRouteIdx : group -> group.fromRouteIdx;
                Group[] slotGroups = governing.get(slot);
                Arrays.sort(slotGroups, Comparator.comparingInt(governedRoute));
                governingGroups[slot] = slotGroups;
                governedRoutes[slot] = Arrays.stream(slotGroups).mapToInt(governedRoute).toArray();
            }

            log.info("Compiled {} groups of trip transfers from {} and to {} routes at stops, into {} slots",
                    groups.size(), forwardOrigins.size(), backwardOrigins.size(), slotStops.size());

            return new TripTransfers(numberOfStops, numberOfGroups,
                    slotStops.stream().mapToInt(Integer::intValue).toArray(), governedRoutes, governingGroups,
                    walkTargets, walksToOwnSlots, arrivalSlots, routeStopGroups, stopSlots, routesWithTransfers,
                    routeStopsWithTransfers);
        }
    }
}
