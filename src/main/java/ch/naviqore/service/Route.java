package ch.naviqore.service;

import org.jetbrains.annotations.NotNull;

public interface Route {
    @NotNull String getRouteId();
    @NotNull String getRouteName();
    @NotNull String getRouteShortName();
    @NotNull String getRouteDescription();
    @NotNull String getRouteType();
    @NotNull String getAgency();
}
