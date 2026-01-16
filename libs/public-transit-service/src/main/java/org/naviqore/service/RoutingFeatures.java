package org.naviqore.service;

/**
 * Supported routing features of the service.
 *
 * @param supportsMaxTransfers        whether the service supports routing with a defined max number of transfers
 * @param supportsMaxTravelDuration   whether the service supports routing with a defined max travel duration (including
 *                                    waiting duration)
 * @param supportsMaxWalkDuration     whether the service supports routing with a defined max walk duration
 * @param supportsMinTransferDuration whether the service supports routing with a defined min transfer duration
 * @param supportsAccessibility       whether the service supports routing for wheelchair users
 * @param supportsBikes               whether the service supports routing for bike users
 * @param supportsTravelModes         whether the service supports routing for different travel modes
 */
public record RoutingFeatures(boolean supportsMaxTransfers, boolean supportsMaxTravelDuration,
                              boolean supportsMaxWalkDuration, boolean supportsMinTransferDuration,
                              boolean supportsAccessibility, boolean supportsBikes, boolean supportsTravelModes) {
}
