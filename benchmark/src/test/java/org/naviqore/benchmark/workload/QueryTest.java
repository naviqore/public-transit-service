package org.naviqore.benchmark.workload;

import org.junit.jupiter.api.Test;
import org.naviqore.benchmark.workload.Query.Location;
import org.naviqore.benchmark.workload.Query.TimeType;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QueryTest {

    private static final OffsetDateTime TIME = OffsetDateTime.parse("2026-01-14T07:30+01:00");

    @Test
    void connectionsReadAsWritten() {
        Query query = Query.connections(Location.stop("ch:1:sloid:7000:4:8"), Location.near("8507000"), TIME,
                TimeType.ARRIVAL, "rail+transfers1");

        assertEquals(query, Query.decode(query.encode()));
    }

    @Test
    void isolinesReadAsWritten() {
        Query query = Query.isolines(Location.near("Parentch:1:sloid:7000"), TIME, TimeType.DEPARTURE, "window60");

        assertEquals(query, Query.decode(query.encode()));
    }

    @Test
    void connectionsNeedATargetAndIsolinesTakeNone() {
        assertThrows(IllegalArgumentException.class,
                () -> new Query(Query.Kind.CONNECTIONS, Location.stop("A"), null, TIME, TimeType.DEPARTURE, "default"));
        assertThrows(IllegalArgumentException.class,
                () -> new Query(Query.Kind.ISOLINES, Location.stop("A"), Location.stop("B"), TIME, TimeType.DEPARTURE,
                        "default"));
    }

    @Test
    void aQueryHasSixFields() {
        assertThrows(IllegalArgumentException.class, () -> Query.decode(List.of("CONNECTIONS", "stop:A")));
    }

}
