package ch.naviqore.gtfs.schedule.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(GtfsScheduleTestExtension.class)
class GtfsScheduleTest {

    private GtfsSchedule schedule;

    @BeforeEach
    void setUp(GtfsScheduleTestBuilder builder) {
        schedule = builder.withAddAgency().withAddCalendars().withAddStops().withAddRoutes().withAddTrips()
                .withAddStopTimes().build();
    }

    @Nested
    class Builder {

        @Test
        void shouldCorrectlyCountAgencies() {
            assertThat(schedule.getAgencies()).hasSize(2);
        }

        @Test
        void shouldCorrectlyCountRoutes() {
            assertThat(schedule.getRoutes()).hasSize(3);
        }

        @Test
        void shouldCorrectlyCountStops() {
            assertThat(schedule.getStops()).hasSize(5);
        }

        @Test
        void shouldCorrectlyCountTrips() {
            assertThat(schedule.getTrips()).hasSize(3);
        }

        @Test
        void shouldCorrectlyCountCalendars() {
            assertThat(schedule.getCalendars()).hasSize(2);
        }
    }

    @Nested
    class NearestStops {

        @Test
        void shouldFindStopsWithin500Meters() {
            assertThat(schedule.getNearestStops(47.3769, 8.5417, 500)).hasSize(3).extracting("id")
                    .containsOnly("stop1", "stop2", "stop3");
        }

        @Test
        void shouldFindNoStopsWhenNoneAreCloseEnough() {
            assertThat(schedule.getNearestStops(47.3800, 8.5500, 100)).isEmpty();
        }
    }

    @Nested
    class NextDepartures {

        @Test
        void shouldReturnNextDeparturesOnWeekday() {
            assertThat(schedule.getNextDepartures("stop1", GtfsScheduleTestBuilder.Moments.WEEKDAY_8_AM,
                    Integer.MAX_VALUE)).hasSize(1);
        }

        @Test
        void shouldReturnNoNextDeparturesOnWeekday() {
            assertThat(schedule.getNextDepartures("stop1", GtfsScheduleTestBuilder.Moments.WEEKDAY_9_AM,
                    Integer.MAX_VALUE)).isEmpty();
        }

        @Test
        void shouldReturnNextDeparturesOnSaturday() {
            assertThat(schedule.getNextDepartures("stop1", GtfsScheduleTestBuilder.Moments.SATURDAY_9_AM,
                    Integer.MAX_VALUE)).hasSize(1);
        }

        @Test
        void shouldReturnNoDeparturesFromUnknownStop() {
            assertThatThrownBy(() -> schedule.getNextDepartures("unknown", LocalDateTime.now(), 1)).isInstanceOf(
                    IllegalArgumentException.class).hasMessage("Stop unknown not found");
        }
    }

    @Nested
    class ActiveTrips {

        @Test
        void shouldReturnActiveTripsOnWeekday() {
            assertThat(schedule.getActiveTrips(GtfsScheduleTestBuilder.Moments.WEEKDAY_8_AM.toLocalDate())).hasSize(2)
                    .extracting("id").containsOnly("trip1", "trip2");
        }

        @Test
        void shouldReturnActiveTripsOnWeekend() {
            assertThat(schedule.getActiveTrips(GtfsScheduleTestBuilder.Moments.SATURDAY_9_AM.toLocalDate())).hasSize(1)
                    .extracting("id").containsOnly("trip3");
        }

        @Test
        void shouldReturnNoActiveTripsForNonServiceDay() {
            assertThat(schedule.getActiveTrips(GtfsScheduleTestBuilder.Validity.END_DATE.plusMonths(1))).isEmpty();
        }
    }
}
