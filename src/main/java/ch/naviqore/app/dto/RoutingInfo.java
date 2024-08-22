package ch.naviqore.app.dto;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@RequiredArgsConstructor
@ToString
@Getter
public class RoutingInfo {
    final boolean supportsMaxNumTransfers;
    final boolean supportsMaxTravelTime;
    final boolean supportsMaxWalkingTime;
    final boolean supportsMinTransferTime;
    final boolean supportsAccessibility;
    final boolean supportsBikes;
    final boolean supportsTravelModes;
}
