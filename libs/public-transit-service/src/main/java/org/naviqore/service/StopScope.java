package org.naviqore.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Defines the scope for resolving stops when querying schedule information.
 */
@RequiredArgsConstructor
@Getter
public enum StopScope {
    /**
     * Only the specific stop provided.
     */
    STRICT,
    /**
     * The specified stop and all its direct children.
     */
    CHILDREN,
    /**
     * The entire station complex (the stop, its parent station, and all sibling stops).
     */
    RELATED,
    /**
     * All stops within the service's configured walkable radius.
     */
    NEARBY
}