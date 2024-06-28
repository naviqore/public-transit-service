package ch.naviqore.app.dto;

import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@EqualsAndHashCode
@ToString
@Getter
public class StopConnection {

    private final Stop stop;
    private final Leg connectingLeg;
    private final Connection connection;

}

