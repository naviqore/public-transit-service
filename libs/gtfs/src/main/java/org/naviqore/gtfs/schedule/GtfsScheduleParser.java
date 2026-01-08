package org.naviqore.gtfs.schedule;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVRecord;
import org.naviqore.gtfs.schedule.model.GtfsScheduleBuilder;
import org.naviqore.gtfs.schedule.type.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.Consumer;

/**
 * GTFS CSV records parser
 *
 * @author munterfi
 */
@Slf4j
class GtfsScheduleParser {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Map<String, DayOfWeek> DAY_MAPPINGS = Map.of("monday", DayOfWeek.MONDAY, "tuesday",
            DayOfWeek.TUESDAY, "wednesday", DayOfWeek.WEDNESDAY, "thursday", DayOfWeek.THURSDAY, "friday",
            DayOfWeek.FRIDAY, "saturday", DayOfWeek.SATURDAY, "sunday", DayOfWeek.SUNDAY);

    private final EnumMap<GtfsScheduleFile, Consumer<CSVRecord>> parsers = new EnumMap<>(GtfsScheduleFile.class);
    private final GtfsScheduleBuilder builder;

    public GtfsScheduleParser(GtfsScheduleBuilder builder) {
        this.builder = builder;
        initializeParsers();
    }

    public void parse(CSVRecord record, GtfsScheduleFile fileType) {
        parsers.getOrDefault(fileType, _ -> {
            throw new IllegalArgumentException("Unsupported GTFS file type for parsing: " + fileType);
        }).accept(record);
    }

    private void initializeParsers() {
        parsers.put(GtfsScheduleFile.AGENCY, this::parseAgency);
        parsers.put(GtfsScheduleFile.CALENDAR, this::parseCalendar);
        parsers.put(GtfsScheduleFile.CALENDAR_DATES, this::parseCalendarDate);
        parsers.put(GtfsScheduleFile.STOPS, this::parseStop);
        parsers.put(GtfsScheduleFile.ROUTES, this::parseRoute);
        parsers.put(GtfsScheduleFile.TRIPS, this::parseTrips);
        parsers.put(GtfsScheduleFile.STOP_TIMES, this::parseStopTimes);
        parsers.put(GtfsScheduleFile.TRANSFERS, this::parseTransfers);
    }

    private void parseAgency(CSVRecord record) {
        builder.addAgency(record.get("agency_id"), record.get("agency_name"), record.get("agency_url"),
                ZoneId.of(record.get("agency_timezone")));
    }

    private void parseCalendar(CSVRecord record) {
        EnumSet<DayOfWeek> serviceDays = EnumSet.noneOf(DayOfWeek.class);
        DAY_MAPPINGS.forEach((key, value) -> {
            if ("1".equals(record.get(key))) {
                serviceDays.add(value);
            }
        });
        builder.addCalendar(record.get("service_id"), serviceDays,
                LocalDate.parse(record.get("start_date"), DATE_FORMATTER),
                LocalDate.parse(record.get("end_date"), DATE_FORMATTER));
    }

    private void parseCalendarDate(CSVRecord record) {
        try {
            builder.addCalendarDate(record.get("service_id"), LocalDate.parse(record.get("date"), DATE_FORMATTER),
                    ExceptionType.parse(record.get("exception_type")));
        } catch (IllegalArgumentException e) {
            log.warn("Skipping invalid calendar date {}: {}", record.get("date"), e.getMessage());
        }
    }

    private void parseStop(CSVRecord record) {
        builder.addStop(record.get("stop_id"), record.get("stop_name"), Double.parseDouble(record.get("stop_lat")),
                Double.parseDouble(record.get("stop_lon")), Utils.getStringFieldOrDefault(record, "parent_station", ""),
                AccessibilityInformation.parse(Utils.getIntFieldOrDefault(record, "wheelchair_boarding", 0)));
    }

    private void parseRoute(CSVRecord record) {
        builder.addRoute(record.get("route_id"), record.get("agency_id"), record.get("route_short_name"),
                record.get("route_long_name"), RouteType.parse(record.get("route_type")));
    }

    private void parseTrips(CSVRecord record) {
        try {
            builder.addTrip(record.get("trip_id"), record.get("route_id"), record.get("service_id"),
                    Utils.getStringFieldOrDefault(record, "trip_headsign", ""),
                    AccessibilityInformation.parse(Utils.getIntFieldOrDefault(record, "wheelchair_accessible", 0)),
                    BikeInformation.parse(Utils.getIntFieldOrDefault(record, "bikes_allowed", 0)));
        } catch (IllegalArgumentException e) {
            log.warn("Skipping invalid trip {}: {}", record.get("trip_id"), e.getMessage());
        }
    }

    private void parseStopTimes(CSVRecord record) {
        try {
            builder.addStopTime(record.get("trip_id"), record.get("stop_id"),
                    ServiceDayTime.parse(record.get("arrival_time")),
                    ServiceDayTime.parse(record.get("departure_time")));
        } catch (IllegalArgumentException e) {
            log.warn("Skipping invalid stop time {}-{}: {}", record.get("trip_id"), record.get("stop_id"),
                    e.getMessage());
        }
    }

    private void parseTransfers(CSVRecord record) {
        String minTransferTime = record.get("min_transfer_time");
        builder.addTransfer(record.get("from_stop_id"), record.get("to_stop_id"),
                TransferType.parse(record.get("transfer_type")),
                minTransferTime.isEmpty() ? null : Integer.parseInt(record.get("min_transfer_time")));
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
