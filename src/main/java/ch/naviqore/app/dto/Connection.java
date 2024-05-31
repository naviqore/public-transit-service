package ch.naviqore.app.dto;

import lombok.*;

import java.util.List;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@EqualsAndHashCode
@ToString
@Getter
public class Connection {

    private final List<Leg> legs;

}

