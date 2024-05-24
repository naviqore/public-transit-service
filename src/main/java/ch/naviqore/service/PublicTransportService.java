package ch.naviqore.service;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface PublicTransportService {
    @NotNull List<StopTime> getNextDepartures(@NotNull Stop stop, @NotNull LocalDate from, @Nullable LocalDate until, int limit);

    @NotNull List<Stop> getStops(@NotNull String like, @NotNull SearchType searchType);

    @Nullable Stop getClosestStop(@NotNull Location location);

    @NotNull List<Stop> getClosestStops(@NotNull Location location, int searchRadius, int limit);

    @NotNull List<Connection> getJourneys(@NotNull Location departureLocation, @NotNull Location arrivalLocation, @NotNull DepartureTime departureTime,
                                 ConnectionQueryConfiguration config);

    @NotNull List<Connection> getJourneys(@NotNull Location departureLocation, @NotNull Location arrivalLocation, @NotNull ArrivalTime arrivalTime,
                                 ConnectionQueryConfiguration config);

    @NotNull Map<Stop, Connection> getShortestPossibleJourneyToStops(@NotNull Location departureLocation, @NotNull DepartureTime departureTime,
                                                            @Nullable ConnectionQueryConfiguration config);

    Stop getStopById(@NotNull String stopId) throws StopNotFoundException;
    Trip getTripById(@NotNull String tripId) throws TripNotFoundException;
    Route getRouteById(@NotNull String routeId) throws RouteNotFoundException;
}
