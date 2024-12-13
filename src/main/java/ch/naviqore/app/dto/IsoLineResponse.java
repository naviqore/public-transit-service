package ch.naviqore.app.dto;

import lombok.*;

import java.util.List;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@EqualsAndHashCode
@ToString
@Getter
public class IsoLineResponse {
    private final List<StopConnection> stopConnections;
    private final String message;
    private final MessageType messageType;
}
