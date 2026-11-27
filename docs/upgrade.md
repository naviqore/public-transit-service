# Upgrading from 4.x to 5.0

Version 5.0 builds the service on a format-neutral timetable, reworks the router around the queries of the RAPTOR
paper (#675), and applies the transfers of particular routes and trips and the pickup and drop-off types of a feed
(#677). This guide lists what operators of the application, clients of its REST API and users of the libraries change
or notice when they upgrade from 4.x. How the router and the service answer is described in [routing.md](routing.md).

## Application

### Properties

The properties moved under `naviqore`, in kebab case, and are grouped by the component that applies them: `gtfs` by
the application, which reads the feed, `service` by the public transit service, `raptor` by its router, `routing` by
the application to the requests of its REST API, and `home-page` by the application. Each property is set by the
environment variable of its name in upper case, with `.` and `-` replaced by `_`, as Spring binds it:
`naviqore.gtfs.static.uri` by `NAVIQORE_GTFS_STATIC_URI`. So every environment variable of the application is renamed,
without a fallback to its 4.x name, which no longer has an effect. The variables of Spring's own properties keep their
names, such as `LOGGING_LEVEL_ROOT`, `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` and `SPRINGDOC_API_DOCS_ENABLED`. A
deployment that sets the properties themselves, in an `application.properties` of its own or as `-D` options, sets
them under their new names.

| 4.x property                              | 4.x variable                              | 5.0 property                                               | 5.0 variable                                               |
|-------------------------------------------|-------------------------------------------|------------------------------------------------------------|------------------------------------------------------------|
| `gtfs.static.uri`                         | `GTFS_STATIC_URI`                         | `naviqore.gtfs.static.uri`                                 | `NAVIQORE_GTFS_STATIC_URI`                                 |
| `gtfs.static.update.cron`                 | `GTFS_STATIC_UPDATE_CRON`                 | `naviqore.gtfs.static.update-cron`                         | `NAVIQORE_GTFS_STATIC_UPDATE_CRON`                         |
| `transfer.duration.between.stops.minimum` | `TRANSFER_DURATION_BETWEEN_STOPS_MINIMUM` | `naviqore.service.transfer.duration.between-stops-minimum` | `NAVIQORE_SERVICE_TRANSFER_DURATION_BETWEEN_STOPS_MINIMUM` |
| `transfer.duration.access.egress`         | `TRANSFER_DURATION_ACCESS_EGRESS`         | `naviqore.service.transfer.duration.access-egress`         | `NAVIQORE_SERVICE_TRANSFER_DURATION_ACCESS_EGRESS`         |
| `walk.search.radius`                      | `WALK_SEARCH_RADIUS`                      | `naviqore.service.walk.search-radius`                      | `NAVIQORE_SERVICE_WALK_SEARCH_RADIUS`                      |
| `walk.calculator.type`                    | `WALK_CALCULATOR_TYPE`                    | `naviqore.service.walk.calculator-type`                    | `NAVIQORE_SERVICE_WALK_CALCULATOR_TYPE`                    |
| `walk.speed`                              | `WALK_SPEED`                              | `naviqore.service.walk.speed`                              | `NAVIQORE_SERVICE_WALK_SPEED`                              |
| `walk.duration.minimum`                   | `WALK_DURATION_MINIMUM`                   | `naviqore.service.walk.duration-minimum`                   | `NAVIQORE_SERVICE_WALK_DURATION_MINIMUM`                   |
| –                                         | –                                         | `naviqore.service.optimize-travel-time`                    | `NAVIQORE_SERVICE_OPTIMIZE_TRAVEL_TIME`                    |
| `raptor.days.to.scan`                     | `RAPTOR_DAYS_TO_SCAN`                     | `naviqore.raptor.days-to-scan`                             | `NAVIQORE_RAPTOR_DAYS_TO_SCAN`                             |
| `raptor.range`                            | `RAPTOR_RANGE`                            | removed                                                    | –                                                          |
| `transfer.duration.same.stop.default`     | `TRANSFER_DURATION_SAME_STOP_DEFAULT`     | `naviqore.raptor.same-stop-transfer-duration-default`      | `NAVIQORE_RAPTOR_SAME_STOP_TRANSFER_DURATION_DEFAULT`      |
| `cache.service.day.size`                  | `CACHE_SERVICE_DAY_SIZE`                  | `naviqore.raptor.stop-time-cache-size`                     | `NAVIQORE_RAPTOR_STOP_TIME_CACHE_SIZE`                     |
| `cache.eviction.strategy`                 | `CACHE_EVICTION_STRATEGY`                 | `naviqore.raptor.stop-time-cache-strategy`                 | `NAVIQORE_RAPTOR_STOP_TIME_CACHE_STRATEGY`                 |
| –                                         | –                                         | `naviqore.routing.time-window-duration-maximum`            | `NAVIQORE_ROUTING_TIME_WINDOW_DURATION_MAXIMUM`            |
| `app.home-page.enabled`                   | `APP_HOME_PAGE_ENABLED`                   | `naviqore.home-page.enabled`                               | `NAVIQORE_HOME_PAGE_ENABLED`                               |

### Defaults and resources

- **Travel time.** A connection without a time window leaves as late as its arrival allows: the service queries the
  router a second time for each connection, backward from its arrival, which takes about as long as the first query
  (`naviqore.service.optimize-travel-time`, on by default). `raptor.range` did this in 4.x for a range of its own, and
  was off by default. Turned off, a connection takes the first trip it can catch, as in 4.x by default.
- **Walks between stops.** The application generates walks between the stops within the walk search radius by default,
  as the library of the service always did: `naviqore.service.transfer.duration.between-stops-minimum` is 180 seconds
  instead of -1. So the benchmark, which builds the service with the defaults of the library, measures what the
  application runs. On the Swiss feed, for 300 random pairs of stops on a Tuesday at 08:00, the router has 636 914
  transfers instead of 95 245: the service builds in 6.8–8.0 s instead of 6.3–6.7 s and takes 664 instead of 635 MB of
  heap, a connection query takes 72–74 ms instead of 59–61 ms (+17–25 %), and an isoline takes 236 instead of 252 ms and
  reaches 47 444 instead of 38 349 stops. Of the 232 pairs with a connection, 139 get a better one and none a worse one:
  29 are connected only by the generated walks, and 110 arrive earlier, by 30 minutes in the median. -1 generates no
  walks, as the application did in 4.x.
- **Walk legs.** In the libraries too, a walk from or to a location is a leg of its own if it takes longer than 60
  seconds, instead of 120 (`ServiceConfig.walkDurationMinimum`). The times of a connection include the first and the
  last mile anyway, so the setting only decides whether a walk is a leg. It counts no access or egress time at either
  end, as its property documents: 4.x counted it for the last mile and for the walks from and to a stop without
  departures or arrivals, but not for the first mile.
- **Cache.** 30 service days are cached instead of 5 (`naviqore.raptor.stop-time-cache-size`). A day now holds the
  stop times of the trips running on it only, 26 MB instead of 293 MB on the Swiss feed, so 30 days take less heap
  than 5 did.
- **Router.** `RaptorConfig` has the defaults of the service, in its constants `DEFAULT_*`, to which those of
  `ServiceConfig` for the router refer: 3 days to scan instead of 1 and 30 cached service days instead of 5.
- **Time windows.** A request with a time window longer than a day is refused with status 400 (`invalid-parameters`).
  A query over a time window runs a search for each departure in it and reads the stop times of every day it reaches,
  so a long window takes much time and memory. `naviqore.routing.time-window-duration-maximum` sets the limit in
  seconds; 4.x had none.
- **Startup.** The rows of `transfers.txt` for particular routes and trips are read and applied, 966 934 of them on the
  Swiss feed, which take about 160 MB of heap once the service is built. A row that cannot be applied, invalid or
  naming a stop a trip does not call at, is skipped with a warning instead of failing the reading of the feed, and the
  reader logs how many records of each file it skipped. A station is a stop of `location_type` 1, so a platform with
  boarding areas stays a stop.

### Home page

The home page at `/` shows the version of the service, its health status and the validity of the loaded schedule,
flagged when it does not include today. It links to the API console, the OpenAPI document and the health and metrics
endpoints only where these are enabled, at their configured paths. It no longer shows the operating system, the Java
version or the build time. The build information, which `/actuator/info` shows, has no build time either: Maven fixes
it at 1980-02-01 for reproducible builds.

## REST API

The endpoints and their parameters are those of 4.x. A connection also has its `departureTime` and `arrivalTime`, as
the connection of an isoline already had. A field of an answer that stands for an option of a query is named after its
parameter, so that each option has one name, and the description of the route type is named as in the service:

| Answer                          | 4.x                        | 5.0                            |
|---------------------------------|----------------------------|--------------------------------|
| `GET /routing`                  | `supportsAccessibility`    | `supportsWheelchairAccessible` |
| `GET /routing`                  | `supportsBikes`            | `supportsBikeAllowed`          |
| `GET /schedule`                 | `hasAccessibility`         | `hasWheelchairAccessible`      |
| `GET /schedule`                 | `hasBikes`                 | `hasBikeAllowed`               |
| Trip of a leg or of a stop time | `bikesAllowed`             | `bikeAllowed`                  |
| Route of a trip                 | `transportMode`            | `travelMode`                   |
| Route of a trip                 | `transportModeDescription` | `routeTypeDescription`         |

The detail of an `unsupported-routing-feature` error names the parameter that is not supported. The answers change:

- **Travel time.** Connections without a time window leave as late as their arrival allows, see above.
- **Times at a location.** A connection from or to a location leaves at the time one sets out from it and arrives at
  the time one reaches it: its `departureTime` and `arrivalTime` include the walk and the access time, also where the
  walk is too short to be a leg of its own. A time window and the maximum travel duration apply to these times.
- **Time windows.** A connection query over a time window reports the connections leaving in it that no other
  connection beats, not even one leaving after the window. A window shorter than the headway can therefore be empty. An
  isoline over a time window reports for each stop the shortest travel time among the connections leaving in the
  window. See [time windows](routing.md#connections-over-a-time-window). A window longer than a day is refused, see
  above.
- **Transfers.** The rows of `transfers.txt` for particular routes or trips apply to their trips, of every type: a
  minimum time of their own, a transfer refused, or staying on board while the vehicle continues as another trip, which
  is a ride of its own. A trip is boarded only where it picks up passengers and left only where it sets them down.
  Staying on board needs neither, and a connection shows it as the next ride without a walk, also where the vehicle
  continues from another stop.
- **Walks** between stops take the time the feed gives for their direction, also when the router searches backward.
- **Ties.** Of equally good connections, the same one is reported on every start of the service.

## Libraries

### Dependencies

`naviqore-public-transit-service` depends on `naviqore-timetable` instead of `naviqore-gtfs`. To build the service
from a GTFS feed, add `naviqore-gtfs`: its `GtfsSchedule` is a `Timetable`. Any other source of a schedule can implement
the interfaces of `naviqore-timetable` instead.

### Service

| 4.x                                                                                            | 5.0                                                                                                         |
|------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------|
| `ServiceConfig.builder().gtfsScheduleRepository(...)`                                          | `ServiceConfig.builder().timetableRepository(...)`                                                          |
| `org.naviqore.service.repo.GtfsScheduleRepository`                                             | `org.naviqore.service.repo.TimetableRepository`                                                             |
| `ServiceConfig.gtfsStaticUpdateCron`, `DEFAULT_GTFS_STATIC_UPDATE_CRON`                        | removed; the application updates its feed (`naviqore.gtfs.static.update-cron`)                              |
| `ServiceConfig.transferDurationSameStopDefault`, `DEFAULT_TRANSFER_DURATION_SAME_STOP_DEFAULT` | `ServiceConfig.raptorSameStopTransferDurationDefault`, `DEFAULT_RAPTOR_SAME_STOP_TRANSFER_DURATION_DEFAULT` |
| `ServiceConfig.raptorRange`, `DEFAULT_RAPTOR_RANGE`                                            | removed; a query with a time window is a range query                                                        |
| –                                                                                              | `ServiceConfig.optimizeTravelTime`, `DEFAULT_OPTIMIZE_TRAVEL_TIME`, `true`                                  |
| `ServiceConfig.cacheServiceDaySize`, `DEFAULT_CACHE_SIZE`                                      | `ServiceConfig.raptorStopTimeCacheSize`, `DEFAULT_RAPTOR_STOP_TIME_CACHE_SIZE`                              |
| `ServiceConfig.cacheEvictionStrategy`, `DEFAULT_CACHE_EVICTION_STRATEGY`                       | `ServiceConfig.raptorStopTimeCacheStrategy`, `DEFAULT_RAPTOR_STOP_TIME_CACHE_STRATEGY`                      |
| package `org.naviqore.service.gtfs.raptor`                                                     | package `org.naviqore.service.raptor`                                                                       |
| `GtfsRaptorService`, `GtfsRaptorServiceInitializer`, `GtfsRaptor…`                             | `RaptorService`, `RaptorServiceInitializer`, `RaptorService…`                                               |
| `GtfsToRaptorConverter`, `GtfsTripMaskProvider`                                                | `TimetableToRaptorConverter`, `TimetableTripMaskProvider`                                                   |
| `StopSortStrategy.getComparator` of `org.naviqore.gtfs.schedule.model.Stop`                    | of `org.naviqore.timetable.model.Stop`                                                                      |

The settings of `ServiceConfig` are named after the properties of the application without `naviqore` and `service`,
such as `walkSpeed` after `naviqore.service.walk.speed`; those starting with `raptor` configure the router.

A repository of a GTFS feed is `() -> new GtfsScheduleReader().read(path)`, as in the [README](../README.md). The
interfaces of `PublicTransitService` and the query configuration are those of 4.x, but for the names of the
[query options](#query-options). The departure and arrival time of a `Connection` include the first and the last mile,
also where a walk is too short to be a leg, see [times at a location](#rest-api).

### GTFS and timetable

- `GtfsSchedule` and the classes of its model implement the interfaces of `org.naviqore.timetable.model`.
- `AccessibilityInformation`, `BikeInformation`, `ServiceDayTime`, `TimeType` and `TransferType` moved from
  `org.naviqore.gtfs.schedule.type` to `org.naviqore.timetable.type`, joined by `PickupDropOffType` and `TravelMode`.
  `GtfsRoutePartitioner` is `org.naviqore.timetable.RoutePartitioner`.
- A stop time has its pickup and drop-off types (`pickupType()`, `dropOffType()`), and a transfer the routes and trips
  it applies to (`getFromRoute()`, `getToRoute()`, `getFromTrip()`, `getToTrip()`).
- A GTFS stop has its `LocationType`. `GtfsScheduleBuilder.addTransfer` refuses a transfer it cannot apply when it is
  added, with an `IllegalArgumentException`, which the reader logs before it skips the row.

### Router

| 4.x                                                          | 5.0                                                                                                                      |
|--------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| `routeEarliestArrival(sourceStops, targetStops, config)`     | `connectionQuery(sourceStops, targetStops, TimeType.DEPARTURE, config)`                                                  |
| `routeLatestDeparture(departureStops, arrivalStops, config)` | `connectionQuery(arrivalStops, departureStops, TimeType.ARRIVAL, config)`: the source stops are where connections arrive |
| `routeIsolines(sourceStops, timeType, config)`               | `isolineQuery(sourceStops, timeType, config)`                                                                            |
| `QueryConfig.raptorRange`, `RaptorConfig.raptorRangeDefault` | `connectionRangeQuery` and `isolineRangeQuery`, with the time window as a `Duration`                                     |

`RaptorRouterBuilder` takes stop times with boarding and alighting, the transfers refused at a stop or between two
(`refuseTransfers`) and the transfers of particular routes or trips (`addTripTransfer`).

### Query options

An option of a query has the name of its REST parameter in the router and the service too, with `maximum` and
`minimum` spelled out, and so have the features of the service and the data of its trips and routes that stand for it.
`ConnectionQueryConfig` keeps its names; the timetable and the GTFS model keep the names of the GTFS fields, such as
`getBikesAllowed()`.

| 4.x                                                        | 5.0                                                    |
|------------------------------------------------------------|--------------------------------------------------------|
| `QueryConfig.bikeAccessible`                               | `QueryConfig.bikeAllowed`                              |
| `QueryConfig.allowedTravelModes`                           | `QueryConfig.travelModes`                              |
| `RoutingFeatures.supportsMaxTransfers()`                   | `RoutingFeatures.supportsMaximumTransfers()`           |
| `RoutingFeatures.supportsMaxTravelDuration()`              | `RoutingFeatures.supportsMaximumTravelDuration()`      |
| `RoutingFeatures.supportsMaxWalkDuration()`                | `RoutingFeatures.supportsMaximumWalkDuration()`        |
| `RoutingFeatures.supportsMinTransferDuration()`            | `RoutingFeatures.supportsMinimumTransferDuration()`    |
| `RoutingFeatures.supportsAccessibility()`                  | `RoutingFeatures.supportsWheelchairAccessible()`       |
| `RoutingFeatures.supportsBikes()`                          | `RoutingFeatures.supportsBikeAllowed()`                |
| `ScheduleInformationService.hasAccessibilityInformation()` | `ScheduleInformationService.hasWheelchairAccessible()` |
| `ScheduleInformationService.hasBikeInformation()`          | `ScheduleInformationService.hasBikeAllowed()`          |
| `ScheduleInformationService.hasTravelModeInformation()`    | `ScheduleInformationService.hasTravelModes()`          |
| `Trip.isBikesAllowed()`                                    | `Trip.isBikeAllowed()`                                 |
| `Route.getRouteType()`                                     | `Route.getTravelMode()`                                |
