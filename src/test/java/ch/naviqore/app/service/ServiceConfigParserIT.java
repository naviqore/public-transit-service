package ch.naviqore.app.service;

import ch.naviqore.service.config.ServiceConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ServiceConfigParserIT {

    @Test
    void testServiceConfigParser_withValidInputs() {
        String gtfsUrl = "src/test/resources/ch/naviqore/gtfs/schedule/sample-feed-1.zip";
        int minimumTransferTime = 200;
        int maxWalkingDistance = 600;
        int walkingSpeed = 4000;
        String walkingCalculatorType = "BEE_LINE_DISTANCE";

        ServiceConfigParser parser = new ServiceConfigParser(gtfsUrl, minimumTransferTime, maxWalkingDistance,
                walkingSpeed, walkingCalculatorType);

        // check that the service config has been loaded correctly
        ServiceConfig config = parser.getServiceConfig();
        assertEquals(gtfsUrl, config.getGtfsUrl());
        assertEquals(minimumTransferTime, config.getMinimumTransferTime());
        assertEquals(maxWalkingDistance, config.getMaxWalkingDistance());
        assertEquals(walkingSpeed, config.getWalkingSpeed());
        assertEquals(ServiceConfig.WalkCalculatorType.BEE_LINE_DISTANCE, config.getWalkCalculatorType());
    }

    @Test
    void testServiceConfigParser_withInvalidWalkCalculatorType() {
        // check that the service config has been loaded correctly
        assertThrows(IllegalArgumentException.class,
                () -> new ServiceConfigParser("src/test/resources/ch/naviqore/gtfs/schedule/invalidSchedule.zip", 200,
                        600, 4000, "INVALID"));
    }

}
