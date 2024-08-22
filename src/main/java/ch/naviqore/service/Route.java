package ch.naviqore.service;

/**
 * A public transit route (also called transit line).
 */
public interface Route {

    String getId();

    String getName();

    String getShortName();

    TravelMode getRouteType();

    String getRouteTypeDescription();

    String getAgency();

}
