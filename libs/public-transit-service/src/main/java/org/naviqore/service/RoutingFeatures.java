package org.naviqore.service;

/**
 * Supported routing features of the service.
 *
 * @param supportsMaxNumTransfers     whether the service supports routing with a defined max number of transfers
 * @param supportsMaxTravelTime       whether the service supports routing with a defined max travel time (including
 *                                    waiting time)
 * @param supportsMaxWalkingDuration  whether the service supports routing with a defined max walking time
 * @param supportsMinTransferDuration whether the service supports routing with a defined min transfer time
 * @param supportsAccessibility       whether the service supports routing for wheelchair users
 * @param supportsBikes               whether the service supports routing for bike users
 * @param supportsTravelModes         whether the service supports routing for different travel modes
 */
public record RoutingFeatures(boolean supportsMaxNumTransfers, boolean supportsMaxTravelTime,
                              boolean supportsMaxWalkingDuration, boolean supportsMinTransferDuration,
                              boolean supportsAccessibility, boolean supportsBikes, boolean supportsTravelModes) {
}
