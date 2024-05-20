package ch.naviqore.raptor.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TripValidatorTest {

    private static final List<String> STOP_IDS = List.of("stop1", "stop2", "stop3");
    private TripValidator tripValidator;

    @BeforeEach
    void setUp() {
        tripValidator = new TripValidator(STOP_IDS);
    }

    @Nested
    class AddTrip {

        @Test
        void shouldAddValidTrips() {
            tripValidator.addTrip("trip1");
            assertDoesNotThrow(() -> tripValidator.addTrip("trip2"));
        }

        @Test
        void shouldNotAddDuplicateTrip() {
            tripValidator.addTrip("trip1");
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> tripValidator.addTrip("trip1"));
            assertEquals("Trip trip1 already exists.", exception.getMessage());
        }
    }

    @Nested
    class AddStopTime {

        @Test
        void shouldAddValidStopTimes() {
            tripValidator.addTrip("trip1");
            StopTime stopTime1 = new StopTime(100, 200);
            StopTime stopTime2 = new StopTime(300, 400);
            StopTime stopTime3 = new StopTime(500, 600);

            assertDoesNotThrow(() -> tripValidator.addStopTime("trip1", "stop1", stopTime1));
            assertDoesNotThrow(() -> tripValidator.addStopTime("trip1", "stop2", stopTime2));
            assertDoesNotThrow(() -> tripValidator.addStopTime("trip1", "stop3", stopTime3));
        }

        @Test
        void shouldNotAddStopTimeToNonExistentTrip() {
            StopTime stopTime = new StopTime(100, 200);
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> tripValidator.addStopTime("trip1", "stop1", stopTime));
            assertEquals("Trip trip1 does not exist.", exception.getMessage());
        }

        @Test
        void shouldNotAddStopTimeForNonExistentStop() {
            tripValidator.addTrip("trip1");
            StopTime stopTime = new StopTime(100, 200);
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> tripValidator.addStopTime("trip1", "nonexistentStop", stopTime));
            assertEquals("Stop nonexistentStop does not exist.", exception.getMessage());
        }

        @Test
        void shouldNotAddDuplicateStopTimes() {
            tripValidator.addTrip("trip1");
            StopTime stopTime = new StopTime(100, 200);
            tripValidator.addStopTime("trip1", "stop1", stopTime);
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> tripValidator.addStopTime("trip1", "stop1", stopTime));
            assertEquals("Stop time for stop stop1 already exists.", exception.getMessage());
        }

        @Test
        void shouldNotAddStopTimesWithOverlapOnPreviousStop() {
            tripValidator.addTrip("trip1");
            StopTime stopTime1 = new StopTime(100, 200);
            StopTime stopTime2 = new StopTime(150, 250); // overlapping times

            tripValidator.addStopTime("trip1", "stop1", stopTime1);
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> tripValidator.addStopTime("trip1", "stop2", stopTime2));
            assertEquals("Departure time at previous stop is greater than arrival time at current stop.",
                    exception.getMessage());
        }

        @Test
        void shouldNotAddStopTimesWithOverlapOnNextStop() {
            tripValidator.addTrip("trip1");
            StopTime stopTime1 = new StopTime(100, 200);
            StopTime stopTime2 = new StopTime(250, 350);
            StopTime stopTime3 = new StopTime(300, 400);

            tripValidator.addStopTime("trip1", "stop1", stopTime1);
            tripValidator.addStopTime("trip1", "stop3", stopTime3);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> tripValidator.addStopTime("trip1", "stop2", stopTime2));
            assertEquals("Departure time at current stop is greater than arrival time at next stop.",
                    exception.getMessage());
        }
    }

    @Nested
    class Validate {

        @Test
        void shouldValidateCompleteRoute() {
            tripValidator.addTrip("trip1");
            StopTime stopTime1 = new StopTime(100, 200);
            StopTime stopTime2 = new StopTime(300, 400);
            StopTime stopTime3 = new StopTime(500, 600);

            tripValidator.addStopTime("trip1", "stop1", stopTime1);
            tripValidator.addStopTime("trip1", "stop2", stopTime2);
            tripValidator.addStopTime("trip1", "stop3", stopTime3);

            assertDoesNotThrow(() -> tripValidator.validate());
        }

        @Test
        void shouldNotValidateWhenStopTimeIsMissing() {
            tripValidator.addTrip("trip1");
            StopTime stopTime1 = new StopTime(100, 200);
            StopTime stopTime2 = new StopTime(300, 400);

            tripValidator.addStopTime("trip1", "stop1", stopTime1);
            tripValidator.addStopTime("trip1", "stop2", stopTime2);

            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> tripValidator.validate());
            assertTrue(exception.getMessage().contains("Stop time at stop stop3 on trip trip1 not set."));
        }
    }
}
