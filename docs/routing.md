# Routing

How connections and isolines are routed: the queries of the RAPTOR router, what the public transit service adds to
them, and the options of both. The router is `naviqore-raptor` (`RaptorAlgorithm`), the service
`naviqore-public-transit-service` (`PublicTransitService`), which the application exposes over REST.

## Terms

| Term                      | Meaning                                                                                                                                                                                     |
|---------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Connection                | A journey from an origin to a destination: rides on trips, walks between stops, and at the service also the walk from the origin and to the destination.                                    |
| Trip, ride                | A trip is a run of a vehicle along a route; a connection rides a trip from one stop to another.                                                                                             |
| Transfer                  | A change between rides: at the same stop, after its same stop transfer time, or on foot to another stop. A connection with _k_ rides has _k − 1_ transfers.                                 |
| Transfer rule             | A rule of the timetable for the transfers at a stop or between two: for all trips, or for particular routes or trips, with a time of its own, staying on board or not possible.             |
| Pickup, drop-off          | Whether a trip picks up and sets down passengers at a stop: it is boarded only where it picks them up, and left only where it sets them down.                                               |
| Round                     | RAPTOR finds the connections with one ride more in each round: round _k_ those with _k_ rides, round 0 the walks from the source stops.                                                     |
| Departure / arrival query | The time type: a departure query asks for connections leaving at or after a time, an arrival query for connections arriving at or before it. The router searches an arrival query backward. |
| Source and target stops   | The stops the search starts from, with the time at each, and the stops it ends at, with the walk from each. For an arrival query, the source stops are where connections arrive.            |
| Beats (Pareto-optimal)    | One connection beats another if it is no worse in each criterion and better in one. A connection no other one beats is Pareto-optimal.                                                      |
| Time window               | A duration after the time of a departure query (before the time of an arrival query). It is the _range_ of the RAPTOR paper.                                                                |
| Isoline                   | The best connection to each stop from a stop or a location (to it, for an arrival query).                                                                                                   |
| Travel time               | From leaving the origin to reaching the destination. Waiting at the origin before the first ride does not count when one can leave later.                                                   |

## The queries of the router

The router answers the queries of the RAPTOR paper
([Delling et al., Transportation Science 2015](https://doi.org/10.1287/trsc.2014.0534)), each for a departure or an
arrival query, chosen by the time type:

| Query                  | Paper               | Answer                                                                                                                                                                                    |
|------------------------|---------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `connectionQuery`      | RAPTOR              | The connections no other one beats in arrival (departure, for an arrival query) and number of rides: the earliest arrival for each number of rides, each with fewer rides arriving later. |
| `connectionRangeQuery` | rRAPTOR             | The connections leaving in the time window (arriving in it) that no other connection beats in departure, arrival and number of rides.                                                     |
| `isolineQuery`         | RAPTOR, one-to-all  | The connection of the earliest arrival (latest departure) at each stop; of equally early ones, the one with the fewest rides.                                                             |
| `isolineRangeQuery`    | rRAPTOR, one-to-all | The connection with the shortest travel time to each stop among those leaving in the time window (arriving in it); of equally short ones, the one leaving earliest (arriving latest).     |

The router routes from stop to stop. The way from the origin to the source stops and from the target stops to the
destination, the first and the last mile, is the caller's: the source stops come with the time at each, the time at
the origin plus the walk to the stop (less it, for an arrival query), and the target stops with the walk from each to
the destination (from the origin to each). The router knows the first and the last mile only as these times and walks,
held as maps of stops; the service computes them (see [The service](#the-service)). A time window applies to all source
stops alike, so it is a window at the origin.

### Examples

Route 1 leaves A at 08:00, 08:15, 08:30 and every 15 minutes, and reaches B five minutes later. Route 2 leaves B at
08:21 and every 30 minutes, and reaches I five minutes later. There is no same stop transfer time.

- **`connectionQuery`**, departing from A at 08:00 to I: `08:00 → 08:26` with two rides. It takes the first trip it
  can catch and waits at B for route 2; leaving at 08:15 arrives as early. The service reports `08:15 → 08:26`, see
  [Optimized travel time](#optimized-travel-time).
- **`connectionRangeQuery`** over 30 minutes from 08:00: `08:15 → 08:26`. The connection at 08:00 is beaten by the one
  at 08:15, which leaves later and arrives as early.
- **`connectionRangeQuery`** over 10 minutes from 08:00: no connection. The only one leaving in the window, at 08:00,
  is beaten by the one leaving at 08:15, after it.
- **`isolineRangeQuery`** over 30 minutes from 08:00: I with `08:15 → 08:26`, 11 minutes, the shortest of the window;
  `isolineQuery` at 08:00 reports I with `08:00 → 08:26`, the earliest arrival.

### Connections over a time window

The range query reports the connections that leave in the window and that **no other connection beats, also one
leaving after the window** (arriving before it, for an arrival query). These are the connections that the queries at
every time in the window find together, each leaving as late as its arrival allows, kept if they leave in the window.
So the windows that follow one another give the connections of a longer one, and a window shorter than the headway may
hold no connection worth taking, as in the example above.

### Isolines over a time window

The isoline range query reports for each stop the **shortest travel time among the connections that leave in the
window**. A connection leaving after the window does not count: it would take the place of the best one leaving in it.
So where a connection walks from a source stop in the window to a trip that calls at the source stop itself only after
the window, it keeps the walk rather than ride the trip from the source stop.

### Connections on foot alone

A connection without a ride, a walk from a source stop to a target stop, can leave at any time. A query reports it at
its time, a range query once, leaving at the start of the window (arriving at its end).

## How the router works

### RAPTOR

RAPTOR works in rounds. Round 0 walks from the source stops. Each round _k_ then scans the routes passing the stops
improved in round _k − 1_, rides their trips to the following stops, and walks from the stops it reached on a trip.
For every stop and round it keeps a label: how the stop was reached, and when. A stop is improved if it is reached
earlier (later, routing backward) than in any round before; only improved stops continue in the next round. Labels
that cannot beat the best arrival at the target, or that exceed the maximum travel duration, are dropped.

A trip boarded at a stop is ridden until a stop reached in the round before allows boarding a better trip there,
which then replaces it. The trip boarded must be better: a label that comes in time for the trip ridden may, once the
transfer time is added, only come in time for a later one, or none.

The trips are those running on the days a query scans. The stop times of a day hold only the trips running on it,
route by route, which the router caches per day. The trips of a route keep their order at every stop, since trips
that overtake one another are held in separate routes, so the trip to take at a stop is the first one leaving at or
after the label (routing backward, the last one arriving at or before it): read one after the other on a route that
runs fewer than 64 trips on the day, and found by binary search on one that runs 64 or more.

### Arrivals on a trip and on foot

A stop is reached on a trip, on foot or at the start, and these arrivals serve differently:

- **Boarding** a trip is possible right after an arrival on foot or at the start, but only after the same stop
  transfer time after an arrival on a trip. So arrivals are compared for boarding with an arrival on foot counting the
  same stop transfer time earlier.
- **Walking** on is only possible after an arrival on a trip: a connection never walks twice in a row.
- **Ending** at a stop, at a target stop or as an isoline, counts the arrival as it is. Of target stops reached
  equally well, a connection ends at one reached on a trip rather than walk on to another, such as from a platform to
  its station, which a feed may join to it by a transfer of no time.

So each stop keeps two best arrivals: the best for boarding and the best on a trip. A trip arriving after a walk that
was earlier for boarding is still the arrival to walk on from: the walk cannot be continued, and the transfers do not
make up for it, since they are generated within a walking radius, so a stop reached on foot from one stop is not always
reached on foot from the stops beyond it. A stop is reported at its earliest arrival of all, on a trip or on foot.

A walk between two stops takes the time the transfer gives for its direction, which may differ from the walk back:
routing backward, the router follows the transfers to a stop, not those from it.

### Transfer rules

The transfers of a stop, its same stop transfer time and its walks, apply to all trips. A stop can refuse transfers:
no trip reached there is then changed for another at the stop, though a walk from it is not refused, as it is no change
at the stop. Two stops can refuse the walk between them, generated or not. A trip is boarded only where it picks up
passengers and left only where it sets them down; the trips of a route that do so at different stops are held in
separate routes, as are trips that overtake one another.

A trip transfer is a rule for particular routes or trips: from the trips of a route, or a trip, arriving at a stop to
the trips of a route, or a trip, departing from the same stop or another one. It allows the transfer in a time of its
own, refuses it, or lets the rider stay on board while the vehicle continues as the departing trip. Of the rules for a
pair of trips, the most specific applies, as GTFS ranks them: the one naming both trips, a trip and the other route, a
trip, both routes, a route; where none applies, the transfers of the stops do. At one stop, the time of a rule is a
same stop transfer time, at least the minimum transfer duration of the query; between two stops it is a walk, no
longer than the longest walk of the query, to which that duration is added. Staying on board takes no time and no
minimum, but is a ride of its own, so a transfer.

Rules that differ by trip do not fit the labels of a stop: a restrictive rule, which refuses the transfer or takes
longer than the stop, would hide an arrival on another route that transfers as the stop allows. So the router keeps
the trips of such rules apart:

- **Slots.** A trip of a route with restrictive rules at a stop reaches a slot of the stop, not the stop: an index
  after the stops, with the id, routes and walks of the stop. The routes at the stop board from the stop and from its
  slots, as the stop allows, except a route that a restrictive rule of the trip in the slot changes to; a walk from a
  slot to a stop where such rules end lands in a slot there too.
- **Boarding by rule.** Where a trip with rules passes the stop they start at (routing backward, end at), the router
  finds the trips that each rule lets the rider board, and boards them in the next round, between two stops after the
  walk the rule gives. As one trip may refuse a transfer that a later one allows, it also looks at the later trips of
  the route that could be boarded where the trip was, on every day it scans; after a trip boarded by a rule, at those
  that rule allows too. A trip no rule applies to changes as the stop allows. The trip a rule lets the rider board
  depends on the timetable and the options of the query alone, so a range query finds it once and keeps it for its
  passes.

Any other rule, staying on board or taking no longer than the stop, hides nothing: its arrivals stay at the stop, and
the rule only adds the faster change. A target stop or an isoline takes the best arrival at the stop or in its slots.
The rules cost only where they apply: the routes and stops without rules are scanned as before.

### Range queries (rRAPTOR)

A range query runs one pass of RAPTOR per time in the window at which leaving the source stops just catches a
trip, from a source stop or after walking from one, the latest first. The labels and best times of a pass are kept for
the next: whoever leaves earlier can wait, so a later departure's label bounds every earlier one, and a pass only
continues from what it reaches earlier than the passes before. Only the first pass routes the whole network.

The best times are kept per round, as in the paper: the best time of a round is the best of that round and the rounds
before it, over all passes so far. A single best time over all rounds would let a later departure with more rides prune
an earlier one with fewer, which is a connection of its own. A round takes over the best times of the round before it
only at the stops where they changed since it last did, so a pass costs what it improves, not the number of stops.

- For **connections**, a first pass leaves at the end of the window: it finds the best connections leaving after the
  window, which bound the passes in the window but are not reported. Each pass then records where it reaches the
  target better than every connection found with as many rides or fewer, and keeps those that leave in the window. So
  no connection is reported that one leaving after the window beats, even if only by leaving later for the same
  arrival, which no pass in the window would see.
- For **isolines**, the first ride of a pass must leave in the rest of the window. After each pass, every stop it
  improved takes its arrival there less the start of the pass as travel time, if that is no longer than the stop's
  shortest so far.

A query without a window is a range query of one pass: its best times need not be kept per round, since its rounds
only improve on the ones before them.

## The service

The service answers connection and isoline queries between stops and locations, and adds to the router:

- **First and last mile.** For a location, the stops within the walk search radius, each with the walk to it and the
  access and egress time; for a stop, the stop and its children. The walks become the walk legs of the connections.
- **Direction.** An arrival query swaps the origin and the destination, so that the router searches backward from the
  destination.
- **Maximum travel duration** of whole connections, first and last mile included. The service filters the connections
  by it rather than pass it to the router, which measures it from the time at the source stops, waiting included.

The time window chooses the query of the router:

| Service query | Without a time window                                          | With a time window     |
|---------------|----------------------------------------------------------------|------------------------|
| Connections   | `connectionQuery`, then optimized travel times (on by default) | `connectionRangeQuery` |
| Isolines      | `isolineQuery`                                                 | `isolineRangeQuery`    |

The connections of an isoline share the rides they have in common: a ride is one leg, which the router reconstructs and
the service maps once, for all the connections that take it.

### Transfers of the timetable

The service hands the transfers of the timetable (GTFS `transfers.txt`) to the router: a transfer for all trips as a
walk between two stops or the same stop transfer time of one, and a transfer for particular routes or trips as a trip
transfer, from and to the sub-routes of its routes that call at its stops. A transfer for a station applies to its
stops, unless one for the stops themselves is given.

| `transfer_type`     | For all trips                                      | For particular routes or trips                   |
|---------------------|----------------------------------------------------|--------------------------------------------------|
| 0 recommended       | the transfers of the stops                         | the time of the stops, even where they refuse it |
| 1 timed             | a walk or a same stop transfer time of no time     | no time                                          |
| 2 minimum time      | a walk or a same stop transfer time of its time    | its time                                         |
| 3 not possible      | refused at the stop, or the walk between the stops | refused                                          |
| 4 in-seat           | –                                                  | staying on board                                 |
| 5 in-seat forbidden | –                                                  | the time of the stops, even where they refuse it |

The query's minimum transfer duration applies to all but staying on board. A trip is boarded where its `pickup_type`
is not 1 and left where its `drop_off_type` is not 1: a pickup or drop-off arranged by phone or with the driver is
possible.

### Optimized travel time

A connection of `connectionQuery` takes the first trip it can catch, so it may leave earlier than its arrival needs.
Without a time window, the service therefore queries the router again for each connection, in the other direction:
backward from its arrival, with as many rides at most and a travel time no longer than its own, which finds the latest
departure that still arrives as early. For an arrival query it is the other way round: forward from the departure, the
earliest arrival. In the example above this turns `08:00 → 08:26` into `08:15 → 08:26`. The second query is bounded by
the connection's travel time and only routes what can improve on it.

The query in the other direction ends at the source stops, with the time at each turned into a walk relative to the
earliest of them (the latest, for an arrival query), so that it ranks departures from the origin as the first query
does. A query with a time window does not need it: the range query reports the latest departures in the window anyway.
It is switched by `ServiceConfig.optimizeTravelTime`, `app.raptor.optimize-travel-time` in the application.

## Options

### Query

`ConnectionQueryConfig` of the service, mapped to `QueryConfig` of the router; the REST parameters of `/routing`.

| Service                   | Router                    | REST                   | Meaning                                                                                                                                                        |
|---------------------------|---------------------------|------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `timeWindowDuration`      | the range queries         | `timeWindowDuration`   | Time window in seconds; 0 for none.                                                                                                                            |
| `maximumWalkDuration`     | `maximumWalkDuration`     | `maxWalkDuration`      | Longest walk in seconds, between stops and to and from a location.                                                                                             |
| `minimumTransferDuration` | `minimumTransferDuration` | `minTransferDuration`  | Time in seconds added to every walk between stops, e.g. to change platforms at ease.                                                                           |
| `maximumTransfers`        | `maximumTransfers`        | `maxTransfers`         | Most transfers, so most rides less one.                                                                                                                        |
| `maximumTravelDuration`   | –                         | `maxTravelDuration`    | Longest travel time in seconds, of whole connections; the service filters by it.                                                                               |
| `wheelchairAccessible`    | `wheelchairAccessible`    | `wheelchairAccessible` | Only trips accessible by wheelchair.                                                                                                                           |
| `bikeAllowed`             | `bikeAccessible`          | `bikeAllowed`          | Only trips that take bikes.                                                                                                                                    |
| `travelModes`             | `allowedTravelModes`      | `travelModes`          | Only trips of these modes.                                                                                                                                     |
| –                         | `maximumTravelDuration`   | –                      | Latest arrival (earliest departure) after (before) the earliest (latest) source time, which counts waiting before the first ride; the service does not set it. |
| –                         | `allowSourceTransfer`     | –                      | Whether to walk from the source stops; the service allows it for a stop, not for a location, whose walks it has already made.                                  |
| –                         | `allowTargetTransfer`     | –                      | Whether to walk to the target stops; likewise.                                                                                                                 |

### Router and service

| Service (`ServiceConfig`)             | Application                                   | Router (`RaptorConfig`)           | Meaning                                                                                   |
|---------------------------------------|-----------------------------------------------|-----------------------------------|-------------------------------------------------------------------------------------------|
| `optimizeTravelTime`                  | `app.raptor.optimize-travel-time`             | –                                 | Optimize the travel time of connections without a time window, see above. Default `true`. |
| `raptorDaysToScan`                    | `app.raptor.days-to-scan`                     | `daysToScan`                      | Service days around the date of a query that are scanned for trips. Default 3.            |
| `transferDurationSameStopDefault`     | `app.transfer.duration.same-stop-default`     | `sameStopTransferDurationDefault` | Same stop transfer time where the timetable gives none, in seconds.                       |
| `transferDurationBetweenStopsMinimum` | `app.transfer.duration.between-stops-minimum` | –                                 | Shortest generated walk between stops; negative for none.                                 |
| `transferDurationAccessEgress`        | `app.transfer.duration.access-egress`         | –                                 | Time to get on and off a vehicle, added to walks.                                         |
| `walkSearchRadius`                    | `app.walk.search-radius`                      | –                                 | Radius in meters for stops near a location and for generated walks between stops.         |
| `cacheServiceDaySize`                 | `app.cache.service-day-size`                  | `stopTimeCacheSize`               | Service days whose stop times are cached. Default 30.                                     |

## Decisions

- **The router routes from stop to stop.** The first and last mile depend on locations, walking and access times,
  which the service knows; the router gets their result as times at the source stops and walks from the target stops.
  That keeps the router's interface and tests free of locations.
- **A range query is a range query.** It answers a time window, as in the paper. The service no longer runs a range
  query to optimize connections without a window: a query in the other direction does that exactly, for as long a
  connection as there is, and its cost does not grow with a range.
- **Time windows of connections and isolines differ** in what they compare with: connections with every connection,
  isolines with the connections leaving in the window, see above.
- **Transfer rules apply to the trips they name, exactly.** A rule for particular trips could be folded into the
  transfer time of its stop, the longest of the trips or the shortest, but either is wrong for some connection: the
  longest misses connections the stop allows, the shortest finds ones the rule refuses. Slots and boarding by rule
  apply each rule to its trips, at a cost only at the stops and routes that have rules, and slots only where a rule
  is restrictive.
- **Staying on board is a ride.** The vehicle continues as another trip, so it counts as a transfer, also towards the
  maximum transfers of a query, but takes no time.
- **The same timetable gives the same answers, on every start.** Of equally good alternatives, RAPTOR keeps the one it
  meets first. The order it meets them in is set when the router is built, never by the order of its input or of a
  map, which can change from one JVM start to the next: stops are numbered by id, routes ordered by their first
  departure and then by id, trips with the same times by id, and the stops of a query by their number.
