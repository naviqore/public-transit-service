package org.naviqore.app.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@RequiredArgsConstructor
@ToString
@Getter
public class RoutingInfo {

    final boolean supportsMaxTransfers;
    final boolean supportsMaxTravelDuration;
    final boolean supportsMaxWalkDuration;
    final boolean supportsMinTransferDuration;
    final boolean supportsAccessibility;
    final boolean supportsBikes;
    final boolean supportsTravelModes;

}
