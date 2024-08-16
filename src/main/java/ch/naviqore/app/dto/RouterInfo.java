package ch.naviqore.app.dto;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor()
public class RouterInfo {
    final boolean supportsAccessibility;
    final boolean supportsBikes;
    final boolean supportsTravelModes;
}
