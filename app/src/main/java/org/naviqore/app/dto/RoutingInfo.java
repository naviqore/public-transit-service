package org.naviqore.app.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@RequiredArgsConstructor
@ToString
@Getter
@Schema(description = "Which routing parameters the service supports; a request that uses one it does not support is refused with 400.")
public class RoutingInfo {

    @Schema(description = "Whether maxTransfers is supported.")
    final boolean supportsMaxTransfers;

    @Schema(description = "Whether maxTravelDuration is supported.")
    final boolean supportsMaxTravelDuration;

    @Schema(description = "Whether maxWalkDuration is supported.")
    final boolean supportsMaxWalkDuration;

    @Schema(description = "Whether minTransferDuration is supported.")
    final boolean supportsMinTransferDuration;

    @Schema(description = "Whether wheelchairAccessible is supported, which needs a schedule that tells which trips are accessible by wheelchair.")
    final boolean supportsWheelchairAccessible;

    @Schema(description = "Whether bikeAllowed is supported, which needs a schedule that tells which trips allow bikes.")
    final boolean supportsBikeAllowed;

    @Schema(description = "Whether travelModes is supported.")
    final boolean supportsTravelModes;

}
