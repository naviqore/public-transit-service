# Upgrading from 4.x to 5.0

Version 5.0 builds the service on a format-neutral timetable, reworks the router around the queries of the RAPTOR
paper (#675), and applies the transfers of particular routes and trips and the pickup and drop-off types of a feed
(#677). This guide lists what operators of the application, clients of its REST API and users of the libraries change
or notice when they upgrade from 4.x. How the router and the service answer is described in [routing.md](routing.md).

## Application

### Properties

The properties moved under `app`, in kebab case. The environment variables keep their names, so a deployment that
sets only them needs no change, except for `RAPTOR_RANGE`, which no longer has an effect. A deployment that sets the
properties themselves, in an `application.properties` of its own or as `-D` options, sets them under their new names.

| 4.x                                       | 5.0                                           | Environment variable                      |
|-------------------------------------------|-----------------------------------------------|-------------------------------------------|
| `gtfs.static.uri`                         | `app.gtfs.static.uri`                         | `GTFS_STATIC_URI`                         |
| `gtfs.static.update.cron`                 | `app.gtfs.static.update-cron`                 | `GTFS_STATIC_UPDATE_CRON`                 |
| `transfer.duration.same.stop.default`     | `app.transfer.duration.same-stop-default`     | `TRANSFER_DURATION_SAME_STOP_DEFAULT`     |
| `transfer.duration.between.stops.minimum` | `app.transfer.duration.between-stops-minimum` | `TRANSFER_DURATION_BETWEEN_STOPS_MINIMUM` |
| `transfer.duration.access.egress`         | `app.transfer.duration.access-egress`         | `TRANSFER_DURATION_ACCESS_EGRESS`         |
| `walk.search.radius`                      | `app.walk.search-radius`                      | `WALK_SEARCH_RADIUS`                      |
| `walk.calculator.type`                    | `app.walk.calculator-type`                    | `WALK_CALCULATOR_TYPE`                    |
| `walk.speed`                              | `app.walk.speed`                              | `WALK_SPEED`                              |
| `walk.duration.minimum`                   | `app.walk.duration-minimum`                   | `WALK_DURATION_MINIMUM`                   |
| `raptor.days.to.scan`                     | `app.raptor.days-to-scan`                     | `RAPTOR_DAYS_TO_SCAN`                     |
| `raptor.range`                            | removed                                       | `RAPTOR_RANGE`, no effect                 |
| –                                         | `app.raptor.optimize-travel-time`             | `RAPTOR_OPTIMIZE_TRAVEL_TIME`             |
| `cache.service.day.size`                  | `app.cache.service-day-size`                  | `CACHE_SERVICE_DAY_SIZE`                  |
| `cache.eviction.strategy`                 | `app.cache.eviction-strategy`                 | `CACHE_EVICTION_STRATEGY`                 |

### Defaults and resources

- **Travel time.** A connection without a time window leaves as late as its arrival allows: the service queries the
  router a second time for each connection, backward from its arrival, which takes about as long as the first query
  (`app.raptor.optimize-travel-time`, on by default). `raptor.range` did this in 4.x for a range of its own, and was
  off by default. Turned off, a connection takes the first trip it can catch, as in 4.x by default.
- **Cache.** 30 service days are cached instead of 5 (`app.cache.service-day-size`). A day now holds the stop times of
  the trips running on it only, 26 MB instead of 293 MB on the Swiss feed, so 30 days take less heap than 5 did.
- **Startup.** The rows of `transfers.txt` for particular routes and trips are read and applied, 966 934 of them on the
  Swiss feed, which take about 160 MB of heap once the service is built.

## REST API

The endpoints, their parameters and the form of their answers are those of 4.x. The answers change:

- **Travel time.** Connections without a time window leave as late as their arrival allows, see above.
- **Time windows.** A connection query over a time window reports the connections leaving in it that no other
  connection beats, not even one leaving after the window. A window shorter than the headway can therefore be empty. An
  isoline over a time window reports for each stop the shortest travel time among the connections leaving in the
  window. See [time windows](routing.md#connections-over-a-time-window).
- **Transfers.** The rows of `transfers.txt` for particular routes or trips apply to their trips, of every type: a
  minimum time of their own, a transfer refused, or staying on board while the vehicle continues as another trip, which
  is a ride of its own. A trip is boarded only where it picks up passengers and left only where it sets them down.
- **Walks** between stops take the time the feed gives for their direction, also when the router searches backward.
- **Ties.** Of equally good connections, the same one is reported on every start of the service.

## Libraries

### Dependencies

`naviqore-public-transit-service` depends on `naviqore-timetable` instead of `naviqore-gtfs`. To build the service
from a GTFS feed, add `naviqore-gtfs`: its `GtfsSchedule` is a `Timetable`. Any other source of a schedule can implement
the interfaces of `naviqore-timetable` instead.

### Service

| 4.x                                                                         | 5.0                                                           |
|-----------------------------------------------------------------------------|---------------------------------------------------------------|
| `ServiceConfig.builder().gtfsScheduleRepository(...)`                       | `ServiceConfig.builder().timetableRepository(...)`            |
| `org.naviqore.service.repo.GtfsScheduleRepository`                          | `org.naviqore.service.repo.TimetableRepository`               |
| `ServiceConfig.raptorRange`                                                 | removed; a query with a time window is a range query          |
| –                                                                           | `ServiceConfig.optimizeTravelTime`, `true` by default         |
| package `org.naviqore.service.gtfs.raptor`                                  | package `org.naviqore.service.raptor`                         |
| `GtfsRaptorService`, `GtfsRaptorServiceInitializer`, `GtfsRaptor…`          | `RaptorService`, `RaptorServiceInitializer`, `RaptorService…` |
| `GtfsToRaptorConverter`, `GtfsTripMaskProvider`                             | `TimetableToRaptorConverter`, `TimetableTripMaskProvider`     |
| `StopSortStrategy.getComparator` of `org.naviqore.gtfs.schedule.model.Stop` | of `org.naviqore.timetable.model.Stop`                        |

A repository of a GTFS feed is `() -> new GtfsScheduleReader().read(path)`, as in the [README](../README.md). The
interfaces of `PublicTransitService` and the query configuration are those of 4.x.

### GTFS and timetable

- `GtfsSchedule` and the classes of its model implement the interfaces of `org.naviqore.timetable.model`.
- `AccessibilityInformation`, `BikeInformation`, `ServiceDayTime`, `TimeType` and `TransferType` moved from
  `org.naviqore.gtfs.schedule.type` to `org.naviqore.timetable.type`, joined by `PickupDropOffType` and `TravelMode`.
  `GtfsRoutePartitioner` is `org.naviqore.timetable.RoutePartitioner`.
- A stop time has its pickup and drop-off types (`pickupType()`, `dropOffType()`), and a transfer the routes and trips
  it applies to (`getFromRoute()`, `getToRoute()`, `getFromTrip()`, `getToTrip()`).

### Router

| 4.x                                                          | 5.0                                                                                                                      |
|--------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| `routeEarliestArrival(sourceStops, targetStops, config)`     | `connectionQuery(sourceStops, targetStops, TimeType.DEPARTURE, config)`                                                  |
| `routeLatestDeparture(departureStops, arrivalStops, config)` | `connectionQuery(arrivalStops, departureStops, TimeType.ARRIVAL, config)`: the source stops are where connections arrive |
| `routeIsolines(sourceStops, timeType, config)`               | `isolineQuery(sourceStops, timeType, config)`                                                                            |
| `QueryConfig.raptorRange`, `RaptorConfig.raptorRangeDefault` | `connectionRangeQuery` and `isolineRangeQuery`, with the time window as a `Duration`                                     |

`RaptorRouterBuilder` takes stop times with boarding and alighting, the transfers refused at a stop or between two
(`refuseTransfers`) and the transfers of particular routes or trips (`addTripTransfer`).
