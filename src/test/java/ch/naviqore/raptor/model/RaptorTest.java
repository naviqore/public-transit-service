package ch.naviqore.raptor.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RaptorTest {

    private Raptor raptor;

    @BeforeEach
    void setUp() {
        RaptorBuilder builder = Raptor.builder();

        // add stops
        builder.addStop("A")
                .addStop("B")
                .addStop("C")
                .addStop("D")
                .addStop("E")

                // add routes
                .addRoute("Route1")
                .addRoute("Route2")

                // add route stops for Route1
                .addRouteStop("A", "Route1")
                .addRouteStop("B", "Route1")
                .addRouteStop("C", "Route1")

                // add stop times for Route1
                .addStopTime("A", "Route1", 300, 320)
                .addStopTime("B", "Route1", 500, 520)
                .addStopTime("C", "Route1", 700, 720)

                // add route stops for Route2
                .addRouteStop("D", "Route2")
                .addRouteStop("B", "Route2")
                .addRouteStop("E", "Route2")

                // add stop times for Route2
                .addStopTime("D", "Route2", 250, 270)
                .addStopTime("B", "Route2", 550, 570)
                .addStopTime("E", "Route2", 750, 770);

        raptor = builder.build();
    }

    @Nested
    class EarliestArrival {
        @Test
        void testRoutingBetweenIntersectingRoutes() {
            raptor.routeEarliestArrival("D", "C", 250);

            // TODO: assertThat...
        }
    }

}
