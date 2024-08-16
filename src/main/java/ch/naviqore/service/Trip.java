package ch.naviqore.service;

import java.util.List;

/**
 * A trip starts on a public transit route and follows its stop sequence.
 * <p>
 * Note: There can be trips with different stop sequences which belong to the same route.
 */
public interface Trip {

    String getId();

    String getHeadSign();

    Route getRoute();

    List<StopTime> getStopTimes();

}
