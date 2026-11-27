package org.naviqore.service;

/**
 * Supported routing features of the service, each named after the option of
 * {@link org.naviqore.service.config.ConnectionQueryConfig} it allows.
 *
 * @param supportsMaximumTransfers        whether the service supports routing with a maximum number of transfers
 * @param supportsMaximumTravelDuration   whether the service supports routing with a maximum travel duration
 *                                        (including waiting duration)
 * @param supportsMaximumWalkDuration     whether the service supports routing with a maximum walk duration
 * @param supportsMinimumTransferDuration whether the service supports routing with a minimum transfer duration
 * @param supportsWheelchairAccessible    whether the service supports routing on wheelchair accessible trips only
 * @param supportsBikeAllowed             whether the service supports routing on trips that allow bikes only
 * @param supportsTravelModes             whether the service supports routing on trips of given travel modes only
 */
public record RoutingFeatures(boolean supportsMaximumTransfers, boolean supportsMaximumTravelDuration,
                              boolean supportsMaximumWalkDuration, boolean supportsMinimumTransferDuration,
                              boolean supportsWheelchairAccessible, boolean supportsBikeAllowed,
                              boolean supportsTravelModes) {
}
