package ch.naviqore.service.gtfs.raptor.convert;

import ch.naviqore.gtfs.schedule.GtfsScheduleReader;
import ch.naviqore.gtfs.schedule.GtfsScheduleTestData;
import ch.naviqore.gtfs.schedule.model.GtfsSchedule;
import ch.naviqore.gtfs.schedule.model.GtfsScheduleBuilder;
import ch.naviqore.gtfs.schedule.type.AccessibilityInformation;
import ch.naviqore.gtfs.schedule.type.RouteType;
import ch.naviqore.gtfs.schedule.type.ServiceDayTime;
import ch.naviqore.gtfs.schedule.type.TransferType;
import ch.naviqore.raptor.RaptorAlgorithm;
import ch.naviqore.raptor.router.*;
import ch.naviqore.service.gtfs.raptor.transfer.TransferGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class GtfsToRaptorConverterIT {

    @Nested
    class FromZip {

        private GtfsSchedule schedule;

        @BeforeEach
        void setUp(@TempDir Path tempDir) throws IOException {
            File zipFile = GtfsScheduleTestData.prepareZipDataset(tempDir);
            schedule = new GtfsScheduleReader().read(zipFile.getAbsolutePath());
        }

        @Test
        void shouldConvertGtfsScheduleToRaptor() {
            GtfsToRaptorConverter mapper = new GtfsToRaptorConverter(schedule, new RaptorConfig());
            RaptorAlgorithm raptor = mapper.convert();
            assertThat(raptor).isNotNull();
        }

    }

    @Nested
    class ManualSchedule {

        @Test
        void noTransfers() throws NoSuchFieldException, IllegalAccessException {
            RaptorBuilderData data = convertRaptor(List.of(), List.of());
            data.assertNumStops(5);
            data.assertNumSameStopTransfers(0);
            data.assertNumNonSameStopTransfers(0);
            List<String> existingStops = List.of("A", "B1", "B2", "C", "C1");
            for (String existingStop : existingStops) {
                data.assertStopExists(existingStop);
                if (existingStop.equals("A")) {
                    data.assertStopHasNumRoutes(existingStop, 2);
                } else {
                    data.assertStopHasNumRoutes(existingStop, 1);
                }
            }
            // they do not have any active trips
            List<String> nonExistingStops = List.of("B", "C2", "D");
            for (String nonExistingStop : nonExistingStops) {
                data.assertStopNotExists(nonExistingStop);
            }
        }

        @Test
        void sameStopTransfersOnAllActiveStops() throws NoSuchFieldException, IllegalAccessException {
            // since C is also a parent stop, additional transfer C1 -> C and C -> C1 will also be generated
            RaptorBuilderData data = convertRaptor(
                    List.of(new Transfer("A", "A", 120), new Transfer("B1", "B1", 120), new Transfer("B2", "B2", 120),
                            new Transfer("C", "C", 120), new Transfer("C1", "C1", 120)), List.of());
            data.assertNumStops(5);
            data.assertNumSameStopTransfers(5);
            // no way to test further as the Raptor.Transfer is not public
            data.assertNumNonSameStopTransfers(2);
            List<String> existingStops = List.of("A", "B1", "B2", "C", "C1");
            for (String existingStop : existingStops) {
                data.assertStopExists(existingStop);
                data.assertSameStopTransferDuration(existingStop, 120);
            }
        }

        @Test
        void sameStopTransfersOnParentStops() throws NoSuchFieldException, IllegalAccessException {
            // since B is not active, but B1 and B2 are active, it should create B1-B1, B1-B2, B2-B2, B2-B1
            // and C and C1 are active thus will have C-C1, C-C, C1-C1, C1-C
            // even though D is specified, it should not be included and the stop should not be created because it does
            // not have any departures
            RaptorBuilderData data = convertRaptor(
                    List.of(new Transfer("A", "A", 120), new Transfer("B", "B", 120), new Transfer("C", "C", 120),
                            new Transfer("D", "D", 120)), List.of());
            data.assertNumStops(5);
            data.assertNumSameStopTransfers(5);
            // no way to test further as the Raptor.Transfer is not public
            data.assertNumNonSameStopTransfers(4);
            List<String> existingStops = List.of("A", "B1", "B2", "C", "C1");
            for (String existingStop : existingStops) {
                data.assertStopExists(existingStop);
                data.assertSameStopTransferDuration(existingStop, 120);
            }
        }

        @Test
        void sameStopTransfersOnParentAndChildStops() throws NoSuchFieldException, IllegalAccessException {
            // since explicit child same stop transfers are defined, the transfer time between B1-B1 and B2-B2 should
            // be 60, whereas create B1-B2, B2-B1 should be 120
            RaptorBuilderData data = convertRaptor(
                    List.of(new Transfer("B", "B", 120), new Transfer("B1", "B1", 60), new Transfer("B2", "B2", 60)),
                    List.of());
            data.assertNumStops(5);
            data.assertNumSameStopTransfers(2);
            // no way to test further as the Raptor.Transfer is not public and make sure that transfer time is 120
            data.assertNumNonSameStopTransfers(2);
            List<String> stops = List.of("B1", "B2");
            for (String stop : stops) {
                data.assertStopExists(stop);
                data.assertSameStopTransferDuration(stop, 60);
            }
        }

        @Test
        void betweenStopTransfersOnParentStops() throws NoSuchFieldException, IllegalAccessException {
            // since B1, B2, C, and C1 are active following transfers should be derived from B-C:
            // B1-C, B1-C1, B2-C, B2-C1, C-B1, C-B2, C1-B1, C1-B2
            RaptorBuilderData data = convertRaptor(
                    List.of(new Transfer("B", "C", 120), new Transfer("C", "B", 120)),
                    List.of());
            data.assertNumSameStopTransfers(0);
            data.assertNumNonSameStopTransfers(8);
        }

        @Test
        void additionalTransfers() throws NoSuchFieldException, IllegalAccessException {
            RaptorBuilderData data = convertRaptor(List.of(new Transfer("B1", "B1", 120)),
                    List.of(new Transfer("B1", "B1", 60), new Transfer("B2", "B2", 60), new Transfer("B1", "B2", 120)));
            data.assertNumStops(5);
            data.assertNumSameStopTransfers(2);
            data.assertNumNonSameStopTransfers(1);
            // since additional transfers should not be applied if gtfs data exists B1-B1 should remain 120
            data.assertSameStopTransferDuration("B1", 120);
            data.assertSameStopTransferDuration("B2", 60);
        }

        static RaptorBuilderData convertRaptor(List<Transfer> scheduleTransfers,
                                               List<Transfer> additionalTransfers) throws NoSuchFieldException, IllegalAccessException {

            GtfsScheduleBuilder scheduleBuilder = GtfsSchedule.builder();
            scheduleBuilder.addCalendar("always", EnumSet.allOf(DayOfWeek.class), LocalDate.MIN, LocalDate.MAX);
            scheduleBuilder.addAgency("agency", "Some Agency", "", "America/New_York");

            scheduleBuilder.addStop("A", "A", 0.0, 0.0);
            scheduleBuilder.addStop("B", "B", 0.0, 0.0);
            scheduleBuilder.addStop("B1", "B1", 0.0, 0.0, "B", AccessibilityInformation.UNKNOWN);
            scheduleBuilder.addStop("B2", "B2", 0.0, 0.0, "B", AccessibilityInformation.UNKNOWN);
            scheduleBuilder.addStop("C", "C", 0.0, 0.0);
            scheduleBuilder.addStop("C1", "C1", 0.0, 0.0, "C", AccessibilityInformation.UNKNOWN);
            scheduleBuilder.addStop("C2", "C2", 0.0, 0.0, "C", AccessibilityInformation.UNKNOWN);
            scheduleBuilder.addStop("D", "D", 0.0, 0.0);

            // Route 1 goes from A, B1, C1
            scheduleBuilder.addRoute("R1", "agency", "R1", "R1", RouteType.parse(1));
            scheduleBuilder.addTrip("T1", "R1", "always", "C1");
            scheduleBuilder.addStopTime("T1", "A", new ServiceDayTime(0), new ServiceDayTime(0));
            scheduleBuilder.addStopTime("T1", "B1", new ServiceDayTime(0), new ServiceDayTime(0));
            scheduleBuilder.addStopTime("T1", "C1", new ServiceDayTime(0), new ServiceDayTime(0));

            // Route 2 goes from A, B2, C
            scheduleBuilder.addRoute("R2", "agency", "R2", "R2", RouteType.parse(1));
            scheduleBuilder.addTrip("T2", "R2", "always", "C");
            scheduleBuilder.addStopTime("T2", "A", new ServiceDayTime(0), new ServiceDayTime(0));
            scheduleBuilder.addStopTime("T2", "B2", new ServiceDayTime(0), new ServiceDayTime(0));
            scheduleBuilder.addStopTime("T2", "C", new ServiceDayTime(0), new ServiceDayTime(0));

            for (Transfer transfer : scheduleTransfers) {
                scheduleBuilder.addTransfer(transfer.fromStopId, transfer.toStopId, TransferType.MINIMUM_TIME,
                        transfer.duration);
            }

            GtfsSchedule schedule = scheduleBuilder.build();

            List<TransferGenerator.Transfer> additionalTransfersList = additionalTransfers.stream()
                    .map(transfer -> new TransferGenerator.Transfer(
                            schedule.getStops().get(transfer.fromStopId()),
                            schedule.getStops().get(transfer.toStopId()),
                            transfer.duration()))
                    .collect(Collectors.toList());


            GtfsToRaptorConverter mapper = new GtfsToRaptorConverter(schedule, additionalTransfersList, new RaptorConfig());
            mapper.convert();

            return new RaptorBuilderData(mapper);
        }

        record Transfer(String fromStopId, String toStopId, int duration) {
        }

        static class RaptorBuilderData {

            Map<String, Integer> stops;
            Map<String, Integer> sameStopTransfers;
            Map<String, Set<String>> stopRoutes;

            int stopTimeSize;
            int routeStopSize;
            int transferSize;

            RaptorBuilderData(GtfsToRaptorConverter converter) throws NoSuchFieldException, IllegalAccessException {
                RaptorRouterBuilder raptorBuilder = getPrivateField(converter, "builder");

                stops = getPrivateField(raptorBuilder, "stops");
                sameStopTransfers = getPrivateField(raptorBuilder, "sameStopTransfers");
                stopRoutes = getPrivateField(raptorBuilder, "stopRoutes");

                stopTimeSize = getPrivateField(raptorBuilder, "stopTimeSize");
                routeStopSize = getPrivateField(raptorBuilder, "routeStopSize");
                transferSize = getPrivateField(raptorBuilder, "transferSize");
            }

            /**
             * A generic method to get the value of a private field from an object.
             *
             * @param object    The object instance from which the field value is retrieved.
             * @param fieldName The name of the field to retrieve.
             * @param <T>       The type of the field value.
             * @return The value of the field, cast to the appropriate type.
             * @throws NoSuchFieldException   If the field does not exist.
             * @throws IllegalAccessException If the field is not accessible.
             */
            public static <T> T getPrivateField(Object object,
                                                String fieldName) throws NoSuchFieldException, IllegalAccessException {
                // Get the class of the object
                Class<?> clazz = object.getClass();

                // Retrieve the field by name
                Field field = clazz.getDeclaredField(fieldName);

                // Bypass Java's access control checks for this field
                field.setAccessible(true);

                // Get the field value and cast it to the appropriate type
                return (T) field.get(object);
            }

            void assertNumStops(int numStops) {
                assertThat(stops.size()).isEqualTo(numStops);
            }

            void assertStopExists(String stopId) {
                assertThat(stops.containsKey(stopId)).isTrue();
            }

            void assertStopNotExists(String stopId) {
                assertThat(stops.containsKey(stopId)).isFalse();
            }

            void assertStopHasNumRoutes(String stopId, int numRoutes) {
                assertThat(stopRoutes.containsKey(stopId)).isTrue();
                assertThat(stopRoutes.get(stopId).size()).isEqualTo(numRoutes);
            }

            void assertNumNonSameStopTransfers(int numTransfers) {
                assertThat(transferSize).isEqualTo(numTransfers);
            }

            void assertNumSameStopTransfers(int numTransfers) {
                assertThat(sameStopTransfers.size()).isEqualTo(numTransfers);
            }

            void assertSameStopTransferDuration(String stopId, int duration) {
                assertThat(sameStopTransfers.containsKey(stopId)).isTrue();
                assertThat(sameStopTransfers.get(stopId)).isEqualTo(duration);
            }

        }

    }

}