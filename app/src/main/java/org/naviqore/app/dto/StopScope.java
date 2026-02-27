package org.naviqore.app.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Scope of stops to include. STRICT: only stop; CHILDREN: stop and children; RELATED: full station complex; NEARBY: walkable area.")
public enum StopScope {
    STRICT,
    CHILDREN,
    RELATED,
    NEARBY
}