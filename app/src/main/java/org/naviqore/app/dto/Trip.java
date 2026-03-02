package org.naviqore.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.jspecify.annotations.Nullable;

import java.util.List;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@EqualsAndHashCode
@ToString
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Trip {

    private final String headSign;
    private final Route route;
    @Nullable
    private final List<StopTime> stopTimes;
    private final boolean bikesAllowed;
    private final boolean wheelchairAccessible;

}

