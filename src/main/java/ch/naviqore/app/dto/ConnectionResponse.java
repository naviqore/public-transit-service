package ch.naviqore.app.dto;

import lombok.*;

import java.util.List;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@EqualsAndHashCode
@ToString
@Getter
public class ConnectionResponse {
    private final List<Connection> connections;
    private final String message;
    private final MessageType messageType;
}
