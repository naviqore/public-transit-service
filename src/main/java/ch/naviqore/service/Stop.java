package ch.naviqore.service;

import ch.naviqore.utils.spatial.GeoCoordinate;

public interface Stop {

    String getId();

    String getName();

    GeoCoordinate getLocation();

}
