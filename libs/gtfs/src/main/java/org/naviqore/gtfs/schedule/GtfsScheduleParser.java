package org.naviqore.gtfs.schedule;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVRecord;
import org.jspecify.annotations.Nullable;
import org.naviqore.gtfs.schedule.model.GtfsScheduleBuilder;
import org.naviqore.gtfs.schedule.type.ExceptionType;
import org.naviqore.gtfs.schedule.type.LocationType;
import org.naviqore.gtfs.schedule.type.RouteType;
import org.naviqore.timetable.type.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * GTFS CSV records parser, in two steps: a record is read into the values the schedule builder takes, which any thread
 * may do, and they are added to the builder, which the thread building the schedule does, in the order of the records.
 * A record that fails to be read fails, or is skipped with a warning, only when it is added, so that the schedule, its
 * warnings and its failures are those of reading and adding each record in turn. An invalid calendar date, trip, stop
 * time or transfer is skipped, as is a transfer that names a call the schedule does not have, such as one at a stop
 * time skipped for lacking its times.
 *
 * @author munterfi
 */
@Slf4j
class GtfsScheduleParser {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Map<String, DayOfWeek> DAY_MAPPINGS = Map.of("monday", DayOfWeek.MONDAY, "tuesday",
            DayOfWeek.TUESDAY, "wednesday", DayOfWeek.WEDNESDAY, "thursday", DayOfWeek.THURSDAY, "friday",
            DayOfWeek.FRIDAY, "saturday", DayOfWeek.SATURDAY, "sunday", DayOfWeek.SUNDAY);
    private static final EnumMap<GtfsScheduleFile, Function<CSVRecord, ParsedRecord>> READERS = new EnumMap<>(
            Map.of(GtfsScheduleFile.AGENCY, GtfsScheduleParser::readAgency, GtfsScheduleFile.CALENDAR,
                    GtfsScheduleParser::readCalendar, GtfsScheduleFile.CALENDAR_DATES,
                    GtfsScheduleParser::readCalendarDate, GtfsScheduleFile.STOPS, GtfsScheduleParser::readStop,
                    GtfsScheduleFile.ROUTES, GtfsScheduleParser::readRoute, GtfsScheduleFile.TRIPS,
                    GtfsScheduleParser::readTrip, GtfsScheduleFile.STOP_TIMES, GtfsScheduleParser::readStopTime,
                    GtfsScheduleFile.TRANSFERS, GtfsScheduleParser::readTransfer));

    private final GtfsScheduleBuilder builder;

    public GtfsScheduleParser(GtfsScheduleBuilder builder) {
        this.builder = builder;
    }

    /**
     * Reads a record into the values of the schedule, on any thread.
     *
     * @param record   the record
     * @param fileType the file it is a record of
     * @return the values, to be added with {@link #add(ParsedRecord)}
     * @throws IllegalArgumentException if the file type is not supported
     */
    static ParsedRecord read(CSVRecord record, GtfsScheduleFile fileType) {
        Function<CSVRecord, ParsedRecord> reader = READERS.get(fileType);
        if (reader == null) {
            throw new IllegalArgumentException("Unsupported GTFS file type for parsing: " + fileType);
        }
        try {
            return reader.apply(record);
        } catch (RuntimeException e) {
            // fails where the record is added, as it did when records were read and added in turn
            return _ -> {
                throw e;
            };
        }
    }

    private static ParsedRecord readAgency(CSVRecord record) {
        String id = record.get("agency_id");
        String name = record.get("agency_name");
        String url = record.get("agency_url");
        ZoneId timezone = ZoneId.of(record.get("agency_timezone"));

        return added(builder -> builder.addAgency(id, name, url, timezone));
    }

    private static ParsedRecord readCalendar(CSVRecord record) {
        EnumSet<DayOfWeek> serviceDays = EnumSet.noneOf(DayOfWeek.class);
        DAY_MAPPINGS.forEach((key, value) -> {
            if ("1".equals(record.get(key))) {
                serviceDays.add(value);
            }
        });
        String id = record.get("service_id");
        LocalDate startDate = LocalDate.parse(record.get("start_date"), DATE_FORMATTER);
        LocalDate endDate = LocalDate.parse(record.get("end_date"), DATE_FORMATTER);

        return added(builder -> builder.addCalendar(id, serviceDays, startDate, endDate));
    }

    private static ParsedRecord readCalendarDate(CSVRecord record) {
        try {
            String serviceId = record.get("service_id");
            String date = record.get("date");
            LocalDate day = LocalDate.parse(date, DATE_FORMATTER);
            ExceptionType type = ExceptionType.parse(record.get("exception_type"));
            return builder -> {
                try {
                    builder.addCalendarDate(serviceId, day, type);
                    return true;
                } catch (IllegalArgumentException e) {
                    return warnCalendarDate(date, e);
                }
            };
        } catch (IllegalArgumentException e) {
            return _ -> warnCalendarDate(record.get("date"), e);
        }
    }

    private static boolean warnCalendarDate(String date, IllegalArgumentException e) {
        log.warn("Skipping invalid calendar date {}: {}", date, e.getMessage());
        return false;
    }

    private static ParsedRecord readStop(CSVRecord record) {
        String id = record.get("stop_id");
        String name = record.get("stop_name");
        double latitude = Double.parseDouble(record.get("stop_lat"));
        double longitude = Double.parseDouble(record.get("stop_lon"));
        String parent = Utils.getStringFieldOrDefault(record, "parent_station", "");
        AccessibilityInformation wheelchairBoarding = AccessibilityInformation.parse(
                Utils.getIntFieldOrDefault(record, "wheelchair_boarding", 0));
        LocationType locationType = LocationType.parse(Utils.getStringFieldOrDefault(record, "location_type", ""));

        return added(
                builder -> builder.addStop(id, name, latitude, longitude, parent, wheelchairBoarding, locationType));
    }

    private static ParsedRecord readRoute(CSVRecord record) {
        String id = record.get("route_id");
        String agency = record.get("agency_id");
        String shortName = record.get("route_short_name");
        String longName = record.get("route_long_name");
        RouteType type = RouteType.parse(record.get("route_type"));

        return added(builder -> builder.addRoute(id, agency, shortName, longName, type));
    }

    private static ParsedRecord readTrip(CSVRecord record) {
        try {
            String id = record.get("trip_id");
            String route = record.get("route_id");
            String service = record.get("service_id");
            String headSign = Utils.getStringFieldOrDefault(record, "trip_headsign", "");
            AccessibilityInformation wheelchairAccessible = AccessibilityInformation.parse(
                    Utils.getIntFieldOrDefault(record, "wheelchair_accessible", 0));
            BikeInformation bikesAllowed = BikeInformation.parse(
                    Utils.getIntFieldOrDefault(record, "bikes_allowed", 0));
            return builder -> {
                try {
                    builder.addTrip(id, route, service, headSign, wheelchairAccessible, bikesAllowed);
                    return true;
                } catch (IllegalArgumentException e) {
                    return warnTrip(id, e);
                }
            };
        } catch (IllegalArgumentException e) {
            return _ -> warnTrip(record.get("trip_id"), e);
        }
    }

    private static boolean warnTrip(String id, IllegalArgumentException e) {
        log.warn("Skipping invalid trip {}: {}", id, e.getMessage());
        return false;
    }

    private static ParsedRecord readStopTime(CSVRecord record) {
        try {
            String trip = record.get("trip_id");
            String stop = record.get("stop_id");
            ServiceDayTime arrival = ServiceDayTime.parse(record.get("arrival_time"));
            ServiceDayTime departure = ServiceDayTime.parse(record.get("departure_time"));
            PickupDropOffType pickupType = PickupDropOffType.parse(
                    Utils.getStringFieldOrDefault(record, "pickup_type", ""));
            PickupDropOffType dropOffType = PickupDropOffType.parse(
                    Utils.getStringFieldOrDefault(record, "drop_off_type", ""));
            return builder -> {
                try {
                    builder.addStopTime(trip, stop, arrival, departure, pickupType, dropOffType);
                    return true;
                } catch (IllegalArgumentException e) {
                    return warnStopTime(trip, stop, e);
                }
            };
        } catch (IllegalArgumentException e) {
            return _ -> warnStopTime(record.get("trip_id"), record.get("stop_id"), e);
        }
    }

    private static boolean warnStopTime(String trip, String stop, IllegalArgumentException e) {
        log.warn("Skipping invalid stop time {}-{}: {}", trip, stop, e.getMessage());
        return false;
    }

    private static ParsedRecord readTransfer(CSVRecord record) {
        TransferRow row = new TransferRow(getIdOrNull(record, "from_stop_id"), getIdOrNull(record, "to_stop_id"),
                getIdOrNull(record, "from_route_id"), getIdOrNull(record, "to_route_id"),
                getIdOrNull(record, "from_trip_id"), getIdOrNull(record, "to_trip_id"));
        try {
            // an empty transfer type is a recommended transfer point
            String transferType = record.get("transfer_type");
            TransferType type = transferType.isEmpty() ? TransferType.RECOMMENDED : TransferType.parse(transferType);
            String minTransferTime = Utils.getStringFieldOrDefault(record, "min_transfer_time", "");
            Integer minimum = minTransferTime.isEmpty() ? null : Integer.parseInt(minTransferTime);
            return builder -> {
                try {
                    builder.addTransfer(row.fromStopId(), row.toStopId(), row.fromRouteId(), row.toRouteId(),
                            row.fromTripId(), row.toTripId(), type, minimum);
                    return true;
                } catch (IllegalArgumentException e) {
                    return warnTransfer(row, e);
                }
            };
        } catch (IllegalArgumentException e) {
            return _ -> warnTransfer(row, e);
        }
    }

    private static boolean warnTransfer(TransferRow row, IllegalArgumentException e) {
        log.warn("Skipping invalid transfer {}: {}", row, e.getMessage());
        return false;
    }

    private static @Nullable String getIdOrNull(CSVRecord record, String fieldName) {
        String id = Utils.getStringFieldOrDefault(record, fieldName, "");
        return id.isEmpty() ? null : id;
    }

    /**
     * The values of a record that are always added, or fail.
     */
    private static ParsedRecord added(Consumer<GtfsScheduleBuilder> adding) {
        return builder -> {
            adding.accept(builder);
            return true;
        };
    }

    /**
     * Adds the values of a record to the schedule builder.
     *
     * @return whether they were added, or skipped with a warning
     */
    public boolean add(ParsedRecord record) {
        return record.addTo(builder);
    }

    /**
     * A record read into the values of the schedule, which are added to the builder when it is added.
     */
    @FunctionalInterface
    interface ParsedRecord {

        /**
         * Adds the values to the builder, or skips them with a warning.
         *
         * @return whether the values were added
         */
        boolean addTo(GtfsScheduleBuilder builder);
    }

    /**
     * The fields that identify a row of transfers.txt, to name it in a warning.
     */
    private record TransferRow(@Nullable String fromStopId, @Nullable String toStopId, @Nullable String fromRouteId,
                               @Nullable String toRouteId, @Nullable String fromTripId, @Nullable String toTripId) {

        private static void add(StringJoiner fields, String name, @Nullable String value) {
            if (value != null) {
                fields.add(name + "=" + value);
            }
        }

        @Override
        public String toString() {
            StringJoiner fields = new StringJoiner(", ", "[", "]");
            add(fields, "from_stop_id", fromStopId);
            add(fields, "to_stop_id", toStopId);
            add(fields, "from_route_id", fromRouteId);
            add(fields, "to_route_id", toRouteId);
            add(fields, "from_trip_id", fromTripId);
            add(fields, "to_trip_id", toTripId);
            return fields.toString();
        }
    }

    // general helpers, whatever default the fields read so far happen to share
    @SuppressWarnings("SameParameterValue")
    private static class Utils {

        // a field may be missing from the header, or from a record that leaves out its last, empty fields
        private static String getStringFieldOrDefault(CSVRecord record, String fieldName, String defaultValue) {
            return record.isSet(fieldName) ? record.get(fieldName).trim() : defaultValue;
        }

        private static int getIntFieldOrDefault(CSVRecord record, String fieldName, int defaultValue) {
            return record.isSet(fieldName) ? Integer.parseInt(record.get(fieldName)) : defaultValue;
        }

    }

}
