package ch.naviqore.app.service;

import ch.naviqore.service.config.ServiceConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ServiceConfigParserIT {

    private static final String GTFS_URL = "src/test/resources/ch/naviqore/gtfs/schedule/sample-feed-1.zip";
    private static final int MINIMUM_TRANSFER_TIME = 200;
    private static final int SAME_STATION_TRANSFER_TIME = 200;
    private static final int MAXIMUM_WALKING_DISTANCE = 600;
    private static final int WALKING_SPEED = 4000;
    private static final String WALKING_CALCULATOR_TYPE = "BEE_LINE_DISTANCE";

    static Stream<Arguments> provideTestCombinations() {
        return Stream.of(Arguments.of(-1, SAME_STATION_TRANSFER_TIME, MAXIMUM_WALKING_DISTANCE, WALKING_SPEED,
                        WALKING_CALCULATOR_TYPE, "Minimum Transfer Time cannot be smaller zero."),
                Arguments.of(MINIMUM_TRANSFER_TIME, -1, MAXIMUM_WALKING_DISTANCE, WALKING_SPEED,
                        WALKING_CALCULATOR_TYPE, "Same Station Transfer Time cannot be smaller zero."),
                Arguments.of(MINIMUM_TRANSFER_TIME, SAME_STATION_TRANSFER_TIME, -1, WALKING_SPEED,
                        WALKING_CALCULATOR_TYPE, "Maximum Walking Distance cannot be smaller zero."),
                Arguments.of(MINIMUM_TRANSFER_TIME, SAME_STATION_TRANSFER_TIME, MAXIMUM_WALKING_DISTANCE, -1,
                        WALKING_CALCULATOR_TYPE, "Walking speed cannot be smaller zero."),
                Arguments.of(MINIMUM_TRANSFER_TIME, SAME_STATION_TRANSFER_TIME, MAXIMUM_WALKING_DISTANCE, 0,
                        WALKING_CALCULATOR_TYPE, "Walking speed cannot be zero."),
                Arguments.of(MINIMUM_TRANSFER_TIME, SAME_STATION_TRANSFER_TIME, MAXIMUM_WALKING_DISTANCE, WALKING_SPEED,
                        "INVALID", "Can't process invalid Walking Calculator Type."));
    }

    @Test
    void testServiceConfigParser_withValidInputs() {
        ServiceConfigParser parser = new ServiceConfigParser(GTFS_URL, MINIMUM_TRANSFER_TIME,
                SAME_STATION_TRANSFER_TIME, MAXIMUM_WALKING_DISTANCE, WALKING_SPEED, WALKING_CALCULATOR_TYPE);

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
                () -> new ServiceConfigParser(GTFS_URL, MINIMUM_TRANSFER_TIME, SAME_STATION_TRANSFER_TIME,
                        MAXIMUM_WALKING_DISTANCE, WALKING_SPEED, "INVALID"));
    }

    @ParameterizedTest(name = "{5}")
    @MethodSource("provideTestCombinations")
    void testServiceConfigParser_withInvalidInputs(int minimumTransferTime, int sameStationTransferTime,
                                                   int maxWalkingDistance, int walkingSpeed,
                                                   String walkingCalculatorType, String message) {
        // check that the service config has been loaded correctly
        assertThrows(IllegalArgumentException.class,
                () -> new ServiceConfigParser(GTFS_URL, minimumTransferTime, sameStationTransferTime,
                        maxWalkingDistance, walkingSpeed, walkingCalculatorType), message);
    }

}
