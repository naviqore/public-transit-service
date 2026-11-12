package org.naviqore.gtfs.schedule;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVRecord;
import org.naviqore.gtfs.schedule.model.GtfsScheduleBuilder;
import org.naviqore.gtfs.schedule.type.ExceptionType;
import org.naviqore.gtfs.schedule.type.RouteType;
import org.naviqore.timetable.type.AccessibilityInformation;
import org.naviqore.timetable.type.BikeInformation;
import org.naviqore.timetable.type.ServiceDayTime;
import org.naviqore.timetable.type.TransferType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * GTFS CSV records parser, in two steps: a record is read into the values the schedule builder takes, which any thread
 * may do, and they are added to the builder, which the thread building the schedule does, in the order of the records.
 * A record that fails to be read fails, or is skipped with a warning, only when it is added, so that the schedule, its
 * warnings and its failures are those of reading and adding each record in turn.
 * <p>
 * A transfer of the schedule applies to all trips between its stops, so a row of transfers.txt for particular routes or
 * trips is left out: taken for all trips, it would set their transfer time to that of a particular connection.
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
    private static final List<String> TRANSFER_ROUTE_AND_TRIP_FIELDS = List.of("from_route_id", "to_route_id",
            "from_trip_id", "to_trip_id");
    // the values of a row of transfers.txt for particular routes or trips, which is left out
    private static final ParsedRecord TRANSFER_FOR_PARTICULAR_TRIPS = _ -> {
    };

    private final GtfsScheduleBuilder builder;
    private long transfersForParticularTrips;

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

        return builder -> builder.addAgency(id, name, url, timezone);
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

        return builder -> builder.addCalendar(id, serviceDays, startDate, endDate);
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
                } catch (IllegalArgumentException e) {
                    warnCalendarDate(date, e);
                }
            };
        } catch (IllegalArgumentException e) {
            return _ -> warnCalendarDate(record.get("date"), e);
        }
    }

    private static void warnCalendarDate(String date, IllegalArgumentException e) {
        log.warn("Skipping invalid calendar date {}: {}", date, e.getMessage());
    }

    private static ParsedRecord readStop(CSVRecord record) {
        String id = record.get("stop_id");
        String name = record.get("stop_name");
        double latitude = Double.parseDouble(record.get("stop_lat"));
        double longitude = Double.parseDouble(record.get("stop_lon"));
        String parent = Utils.getStringFieldOrDefault(record, "parent_station", "");
        AccessibilityInformation wheelchairBoarding = AccessibilityInformation.parse(
                Utils.getIntFieldOrDefault(record, "wheelchair_boarding", 0));

        return builder -> builder.addStop(id, name, latitude, longitude, parent, wheelchairBoarding);
    }

    private static ParsedRecord readRoute(CSVRecord record) {
        String id = record.get("route_id");
        String agency = record.get("agency_id");
        String shortName = record.get("route_short_name");
        String longName = record.get("route_long_name");
        RouteType type = RouteType.parse(record.get("route_type"));

        return builder -> builder.addRoute(id, agency, shortName, longName, type);
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
                } catch (IllegalArgumentException e) {
                    warnTrip(id, e);
                }
            };
        } catch (IllegalArgumentException e) {
            return _ -> warnTrip(record.get("trip_id"), e);
        }
    }

    private static void warnTrip(String id, IllegalArgumentException e) {
        log.warn("Skipping invalid trip {}: {}", id, e.getMessage());
    }

    private static ParsedRecord readStopTime(CSVRecord record) {
        try {
            String trip = record.get("trip_id");
            String stop = record.get("stop_id");
            ServiceDayTime arrival = ServiceDayTime.parse(record.get("arrival_time"));
            ServiceDayTime departure = ServiceDayTime.parse(record.get("departure_time"));
            return builder -> {
                try {
                    builder.addStopTime(trip, stop, arrival, departure);
                } catch (IllegalArgumentException e) {
                    warnStopTime(trip, stop, e);
                }
            };
        } catch (IllegalArgumentException e) {
            return _ -> warnStopTime(record.get("trip_id"), record.get("stop_id"), e);
        }
    }

    private static void warnStopTime(String trip, String stop, IllegalArgumentException e) {
        log.warn("Skipping invalid stop time {}-{}: {}", trip, stop, e.getMessage());
    }

    private static ParsedRecord readTransfer(CSVRecord record) {
        // a row for particular routes or trips applies to them alone, a transfer between stops to all trips
        if (TRANSFER_ROUTE_AND_TRIP_FIELDS.stream()
                .anyMatch(field -> !Utils.getStringFieldOrDefault(record, field, "").isEmpty())) {
            return TRANSFER_FOR_PARTICULAR_TRIPS;
        }
        String minTransferTime = record.get("min_transfer_time");
        String from = record.get("from_stop_id");
        String to = record.get("to_stop_id");
        TransferType type = TransferType.parse(record.get("transfer_type"));
        Integer minimum = minTransferTime.isEmpty() ? null : Integer.parseInt(record.get("min_transfer_time"));

        return builder -> builder.addTransfer(from, to, type, minimum);
    }

    /**
     * Adds the values of a record to the schedule builder.
     */
    public void add(ParsedRecord record) {
        if (record == TRANSFER_FOR_PARTICULAR_TRIPS) {
            transfersForParticularTrips++;
            return;
        }
        record.addTo(builder);
    }

    /**
     * The number of rows of transfers.txt for particular routes or trips, which were left out.
     */
    long transfersForParticularTrips() {
        return transfersForParticularTrips;
    }

    /**
     * A record read into the values of the schedule, which are added to the builder when it is added.
     */
    @FunctionalInterface
    interface ParsedRecord {

        void addTo(GtfsScheduleBuilder builder);
    }

    private static class Utils {

        private static String getStringFieldOrDefault(CSVRecord record, String fieldName, String defaultValue) {
            return record.isMapped(fieldName) ? record.get(fieldName).trim() : defaultValue;
        }

        private static int getIntFieldOrDefault(CSVRecord record, String fieldName, int defaultValue) {
            return record.isMapped(fieldName) ? Integer.parseInt(record.get(fieldName)) : defaultValue;
        }

    }

}
