package org.naviqore.service;

import org.naviqore.utils.spatial.GeoCoordinate;
import org.naviqore.utils.spatial.Location;

public interface Stop extends Location<GeoCoordinate> {

    String getId();

    String getName();

    GeoCoordinate getCoordinate();

}
