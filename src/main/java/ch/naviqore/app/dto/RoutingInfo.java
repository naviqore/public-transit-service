package ch.naviqore.app.dto;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

@RequiredArgsConstructor
@ToString
@Getter
@Accessors(fluent = true)
public class RoutingInfo {
    final boolean supportsMaxNumTransfers;
    final boolean supportsMaxTravelTime;
    final boolean supportsMaxWalkingDuration;
    final boolean supportsMinTransferDuration;
    final boolean supportsAccessibility;
    final boolean supportsBikes;
    final boolean supportsTravelModes;
}
