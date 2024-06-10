package ch.naviqore.app.service;

import ch.naviqore.service.config.ServiceConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ServiceConfigParserIT {

    private static final String GTFS_URL = "src/test/resources/ch/naviqore/gtfs/schedule/sample-feed-1.zip";
    private static final int MINIMUM_TRANSFER_TIME = 200;
    private static final int MAXIMUM_WALKING_DISTANCE = 600;
    private static final int WALKING_SPEED = 4000;
    private static final String WALKING_CALCULATOR_TYPE = "BEE_LINE_DISTANCE";

    @Test
    void testServiceConfigParser_withValidInputs() {
        ServiceConfigParser parser = new ServiceConfigParser(GTFS_URL, MINIMUM_TRANSFER_TIME, MAXIMUM_WALKING_DISTANCE,
                WALKING_SPEED, WALKING_CALCULATOR_TYPE);

        // check that the service config has been loaded correctly
        ServiceConfig config = parser.getServiceConfig();
        assertEquals(GTFS_URL, config.getGtfsUrl());
        assertEquals(MINIMUM_TRANSFER_TIME, config.getMinimumTransferTime());
        assertEquals(MAXIMUM_WALKING_DISTANCE, config.getMaxWalkingDistance());
        assertEquals(WALKING_SPEED, config.getWalkingSpeed());
        assertEquals(ServiceConfig.WalkCalculatorType.BEE_LINE_DISTANCE, config.getWalkCalculatorType());
    }

    @Test
    void testServiceConfigParser_withInvalidWalkCalculatorType() {
        // check that the service config has been loaded correctly
        assertThrows(IllegalArgumentException.class,
                () -> new ServiceConfigParser(GTFS_URL, MINIMUM_TRANSFER_TIME, MAXIMUM_WALKING_DISTANCE, WALKING_SPEED,
                        "INVALID"));
    }

}
