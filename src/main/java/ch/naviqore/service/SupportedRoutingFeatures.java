package ch.naviqore.service;

/**
 * Supported routing features of the service.
 *
 * @param supportsMaxNumTransfers whether the service supports routing with a defined max number of transfers
 * @param supportsMaxTravelTime   whether the service supports routing with a defined max travel time (including waiting
 *                                time)
 * @param supportsMaxWalkingTime  whether the service supports routing with a defined max walking time
 * @param supportsMinTransferTime whether the service supports routing with a defined min transfer time
 * @param supportsWheelchair      whether the service supports routing for wheelchair users
 * @param supportsBike            whether the service supports routing for bike users
 * @param supportsTravelMode      whether the service supports routing for different travel modes
 */
public record SupportedRoutingFeatures(boolean supportsMaxNumTransfers, boolean supportsMaxTravelTime,
                                       boolean supportsMaxWalkingTime, boolean supportsMinTransferTime,
                                       boolean supportsWheelchair, boolean supportsBike, boolean supportsTravelMode) {
}
