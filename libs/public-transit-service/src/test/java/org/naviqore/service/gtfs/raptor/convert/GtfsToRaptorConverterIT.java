package org.naviqore.service.gtfs.raptor.convert;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.naviqore.gtfs.schedule.GtfsScheduleReader;
import org.naviqore.gtfs.schedule.GtfsScheduleTestData;
import org.naviqore.gtfs.schedule.model.GtfsSchedule;
import org.naviqore.gtfs.schedule.model.Stop;
import org.naviqore.gtfs.schedule.type.TransferType;
import org.naviqore.raptor.RaptorAlgorithm;
import org.naviqore.raptor.router.RaptorConfig;
import org.naviqore.raptor.router.RaptorRouterBuilder;
import org.naviqore.service.gtfs.raptor.GtfsRaptorTestSchedule;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.*;

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
            GtfsToRaptorConverter converter = new GtfsToRaptorConverter(new RaptorConfig(), schedule);
            RaptorAlgorithm raptor = converter.run();
            assertThat(raptor).isNotNull();
        }

    }

    @Nested
    class ManualSchedule {
        /**
         * All tests run with a fixed set of stops and routes in a GTFS schedule as shown below:
         * <pre>
         *     |--------B1------------C1-----------D1
         *     |
         * A---|       (B)      |-----C-------|   (D)           (E)
         *     |                |             |
         *     |--------B2 -----|    (C2)     |----D2
         * </pre>
         * <ul>
         *     <li><b>Route 1</b>: Passes through A - B1 - C1 - D1</li>
         *     <li><b>Route 2</b>: Passes through A - B2 - C  - D2</li>
         * </ul>
         * Stops B, C2, D, E have no departures/arrivals and should not be included in the raptor conversion.
         * Stops B, C, D are parents of stops (B1, B2), (C1, C2) and (D1, D2) respectively.
         */
        static RaptorBuilderData convertRaptor(List<Transfer> scheduleTransfers,
                                               List<TransferGenerator> transferGenerators) throws NoSuchFieldException, IllegalAccessException {
            // build GTFS test schedule
            GtfsRaptorTestSchedule builder = new GtfsRaptorTestSchedule();
            for (Transfer transfer : scheduleTransfers) {
                builder.addTransfer(transfer.fromStopId, transfer.toStopId, TransferType.MINIMUM_TIME,
                        transfer.duration);
            }
            GtfsSchedule schedule = builder.build();

            // run converter
            GtfsToRaptorConverter converter = new GtfsToRaptorConverter(new RaptorConfig(), schedule,
                    transferGenerators);
            converter.run();

            return new RaptorBuilderData(converter);
        }

        @Test
        void noTransfers() throws NoSuchFieldException, IllegalAccessException {
            RaptorBuilderData data = convertRaptor(List.of(), List.of());
            Set<String> stopsWithDepartures = Set.of("A", "B1", "B2", "C", "C1", "D1", "D2");

            data.assertStops(stopsWithDepartures);
            data.assertSameStopTransfers(Set.of());
            data.assertBetweenStopTransfers(Set.of());

            for (String stopWithDeparture : stopsWithDepartures) {
                data.assertStopExists(stopWithDeparture);
                if (stopWithDeparture.equals("A")) {
                    data.assertStopHasNumRoutes(stopWithDeparture, 2);
                } else {
                    data.assertStopHasNumRoutes(stopWithDeparture, 1);
                }
            }

            // they do not have any departures, and therefore should not be included in the raptor data
            Set<String> stopsWithoutDepartures = Set.of("B", "C2", "D");
            for (String stopWithoutDeparture : stopsWithoutDepartures) {
                data.assertStopWithNoDeparturesNotExists(stopWithoutDeparture);
            }
        }

        @Test
        void sameStopTransfersOnAllStopsWithDepartures() throws NoSuchFieldException, IllegalAccessException {
            List<Transfer> sameStopTransfers = List.of(new Transfer("A", "A", 120), new Transfer("B1", "B1", 120),
                    new Transfer("B2", "B2", 120), new Transfer("C", "C", 120), new Transfer("C1", "C1", 120));

            RaptorBuilderData data = convertRaptor(sameStopTransfers, List.of());

            data.assertStops(Set.of("A", "B1", "B2", "C", "C1", "D1", "D2"));
            data.assertSameStopTransfers(Set.of("A-120", "B1-120", "B2-120", "C-120", "C1-120"));
            // since C is also a parent stop, additional transfer C1 -> C and C -> C1 will also be generated
            data.assertBetweenStopTransfers(Set.of("C-C1", "C1-C"));
        }

        @Test
        void sameStopTransfersOnParentStops() throws NoSuchFieldException, IllegalAccessException {
            List<Transfer> sameStopTransfers = List.of(new Transfer("A", "A", 120), new Transfer("B", "B", 120),
                    new Transfer("C", "C", 120), new Transfer("D", "D", 120), new Transfer("E", "D", 120));

            RaptorBuilderData data = convertRaptor(sameStopTransfers, List.of());

            data.assertStops(Set.of("A", "B1", "B2", "C", "C1", "D1", "D2"));
            data.assertSameStopTransfers(Set.of("A-120", "B1-120", "B2-120", "C-120", "C1-120", "D1-120", "D2-120"));

            // B, D and E are not active, but B1 and B2, C and C1 and D1 and D2 are active:
            data.assertBetweenStopTransfers(Set.of("B1-B2", "B2-B1", "C-C1", "C1-C", "D1-D2", "D2-D1"));
        }

        @Test
        void sameStopTransfersOnParentAndChildStops() throws NoSuchFieldException, IllegalAccessException {
            List<Transfer> sameStopTransfers = List.of(new Transfer("B", "B", 120), new Transfer("B1", "B1", 60),
                    new Transfer("B2", "B2", 60));

            RaptorBuilderData data = convertRaptor(sameStopTransfers, List.of());

            data.assertStops(Set.of("A", "B1", "B2", "C", "C1", "D1", "D2"));
            // since explicit child same stop transfers are defined, the transfer time between B1-B1 and B2-B2 should
            // be 60:
            data.assertSameStopTransfers(Set.of("B1-60", "B2-60"));
            data.assertBetweenStopTransfers(Set.of("B1-B2", "B2-B1"));
        }

        @Test
        void betweenStopTransfersOnParentStops() throws NoSuchFieldException, IllegalAccessException {
            List<Transfer> scheduleTransfers = List.of(new Transfer("B", "C", 120), new Transfer("C", "B", 120));

            RaptorBuilderData data = convertRaptor(scheduleTransfers, List.of());

            data.assertStops(Set.of("A", "B1", "B2", "C", "C1", "D1", "D2"));
            data.assertSameStopTransfers(Set.of());

            // since B1, B2, C, and C1 are active following transfers should be derived from B-C:
            data.assertBetweenStopTransfers(Set.of("B1-C", "B1-C1", "B2-C", "B2-C1", "C-B1", "C-B2", "C1-B1", "C1-B2"));
        }

        @Test
        void additionalTransfers() throws NoSuchFieldException, IllegalAccessException {
            List<Transfer> scheduleTransfers = List.of(new Transfer("B1", "B1", 120));

            // Create a list of TransferGenerators that generate specific transfers
            List<TransferGenerator> transferGenerators = List.of(new SimpleTransferGenerator(
                    List.of(new Transfer("B1", "B1", 60), new Transfer("B2", "B2", 60),
                            new Transfer("B1", "B2", 120))));

            RaptorBuilderData data = convertRaptor(scheduleTransfers, transferGenerators);

            data.assertStops(Set.of("A", "B1", "B2", "C", "C1", "D1", "D2"));
            // since LL should not be applied if gtfs data exists B1-B1 should remain 120
            data.assertSameStopTransfers(Set.of("B1-120", "B2-60"));
            data.assertBetweenStopTransfers(Set.of("B1-B2"));
        }

        @Test
        void multipleTransferGenerators() throws NoSuchFieldException, IllegalAccessException {
            List<Transfer> scheduleTransfers = List.of(new Transfer("B1", "B1", 120));

            TransferGenerator transferGenerator1 = new SimpleTransferGenerator(
                    List.of(new Transfer("B1", "B1", 90), new Transfer("B2", "B2", 90)));

            TransferGenerator transferGenerator2 = new SimpleTransferGenerator(
                    List.of(new Transfer("B1", "B1", 60), new Transfer("B2", "B2", 60), new Transfer("C1", "C1", 60)));

            List<TransferGenerator> transferGenerators = List.of(transferGenerator1, transferGenerator2);

            RaptorBuilderData data = convertRaptor(scheduleTransfers, transferGenerators);
            data.assertStops(Set.of("A", "B1", "B2", "C", "C1", "D1", "D2"));
            // because schedule transfers have the highest priority B1-B1 should be 120
            // in case of multiple transfer generators the first should have the highest and the last should have the
            // lowest priority, therefore B2-B2 should be 90 (defined in both) and C1-C1 should be 60 (only defined
            // in last transfer generator)
            data.assertSameStopTransfers(Set.of("B1-120", "B2-90", "C1-60"));

        }

        record Transfer(String fromStopId, String toStopId, int duration) {
        }

        static class RaptorBuilderData {

            Map<String, Integer> stops;
            Map<String, Set<String>> stopRoutes;

            List<String> betweenStopTransferIds;
            List<String> sameStopTransferKeys;

            int stopTimeSize;
            int routeStopSize;
            int transferSize;

            RaptorBuilderData(GtfsToRaptorConverter converter) throws NoSuchFieldException, IllegalAccessException {
                RaptorRouterBuilder raptorBuilder = getPrivateField(converter, "builder");

                stops = getPrivateField(raptorBuilder, "stops");
                stopRoutes = getPrivateField(raptorBuilder, "stopRoutes");

                betweenStopTransferIds = getBetweenStopTransferIds(raptorBuilder);
                sameStopTransferKeys = getSameStopTransferKeys(raptorBuilder);

                stopTimeSize = getPrivateField(raptorBuilder, "stopTimeSize");
                routeStopSize = getPrivateField(raptorBuilder, "routeStopSize");
                transferSize = getPrivateField(raptorBuilder, "transferSize");
            }

            private static List<String> getSameStopTransferKeys(
                    RaptorRouterBuilder raptorBuilder) throws NoSuchFieldException, IllegalAccessException {
                Map<String, Integer> sameStopTransfers = getPrivateField(raptorBuilder, "sameStopTransfers");
                List<String> keys = new ArrayList<>();
                sameStopTransfers.forEach((key, value) -> keys.add(key + "-" + value));

                return keys;
            }

            @SuppressWarnings("unchecked")
            private static List<String> getBetweenStopTransferIds(
                    RaptorRouterBuilder raptorBuilder) throws NoSuchFieldException, IllegalAccessException {
                Map<String, Object> transfers = getPrivateField(raptorBuilder, "transfers");

                List<String> betweenStopTransferIds = new ArrayList<>();
                for (var entry : transfers.entrySet()) {
                    Map<String, ?> transfersAtCurrentStop = (Map<String, ?>) entry.getValue();
                    betweenStopTransferIds.addAll(transfersAtCurrentStop.keySet());
                }

                return betweenStopTransferIds;
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
            @SuppressWarnings("unchecked")
            private static <T> T getPrivateField(Object object,
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

            void assertStops(Set<String> ids) {
                assertThat(stops.keySet()).containsExactlyInAnyOrderElementsOf(ids);
            }

            void assertStopExists(String stopId) {
                assertThat(stops.containsKey(stopId)).isTrue();
            }

            void assertStopWithNoDeparturesNotExists(String stopId) {
                assertThat(stops.containsKey(stopId)).isFalse();
            }

            void assertStopHasNumRoutes(String stopId, int numRoutes) {
                assertThat(stopRoutes.containsKey(stopId)).isTrue();
                assertThat(stopRoutes.get(stopId).size()).isEqualTo(numRoutes);
            }

            void assertSameStopTransfers(Set<String> keys) {
                assertThat(sameStopTransferKeys).containsExactlyInAnyOrderElementsOf(keys);
            }

            void assertBetweenStopTransfers(Set<String> ids) {
                assertThat(betweenStopTransferIds).containsExactlyInAnyOrderElementsOf(ids);
            }

        }

        class SimpleTransferGenerator implements TransferGenerator {

            private final List<ManualSchedule.Transfer> transfers;

            public SimpleTransferGenerator(List<ManualSchedule.Transfer> transfers) {
                this.transfers = transfers;
            }

            @Override
            public List<Transfer> generateTransfers(Collection<Stop> stops) {
                return this.transfers.stream()
                        .map(transfer -> new Transfer(stops.stream()
                                .filter(stop -> stop.getId().equals(transfer.fromStopId()))
                                .findFirst()
                                .orElseThrow(), stops.stream()
                                .filter(stop -> stop.getId().equals(transfer.toStopId()))
                                .findFirst()
                                .orElseThrow(), transfer.duration))
                        .toList();
            }
        }

    }

}